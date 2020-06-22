(ns avisi.crux.datastore
  (:require
    [crux.node :as n]
    [crux.db :as db]
    [crux.io :as cio]
    [crux.tx :as tx]
    [crux.tx.event :as txe]
    [taoensso.nippy :as nippy]
    [clojure.tools.logging :as log]
    [clojure.spec.alpha :as s]
    [crux.bus :as bus])
  (:import
    [com.google.cloud.datastore Datastore KeyFactory Entity Blob BlobValue Key Query StructuredQuery$OrderBy
     TimestampValue DatastoreException EntityQuery$Builder EntityQuery KeyQuery$Builder KeyQuery
     StructuredQuery$PropertyFilter QueryResults Cursor]
    [com.google.cloud Timestamp]
    [com.google.rpc Code]))

(set! *warn-on-reflection* true)

(defmethod bus/event-spec ::submitted-tx [_] (s/keys :req [::tx/tx-id ::tx/tx-time]))

(def ^String transaction-kind "Transaction")
(def ^String doc-kind "Document")

(defn ^Key doc-id->key [{:keys [^Datastore datastore namespace]} id]
  (->
    (.newKeyFactory datastore)
    ^KeyFactory (.setKind doc-kind)
    ^KeyFactory (.setNamespace namespace)
    (.newKey (str id))))

(defn blob->clj [^Blob b] (nippy/thaw (.toByteArray b)))

(defn ^BlobValue clj->blob [c]
  (->
    (BlobValue/newBuilder (Blob/copyFrom ^bytes (nippy/freeze c)))
    (.setExcludeFromIndexes true)
    ^BlobValue (.build)))

(defn ^Entity document->entity [document-store id doc]
  (->
    (Entity/newBuilder (doc-id->key document-store id))
    (.set "doc" (clj->blob doc))
    (.build)))

(defn get-docs-by-ids
  [{:keys [^Datastore datastore]
    :as document-store}
   ids]
  (let [id-mapping (into {} (map (juxt #(doc-id->key document-store %) identity)) ids)]
    (reduce (fn [m ^Entity e] (assoc m (get id-mapping (.getKey e)) (blob->clj (.getBlob e "doc"))))
      {}
      (->>
        (keys id-mapping)
        (partition-all 1000)
        (mapcat #(.fetch datastore (into-array Key %)))))))

(defn save-docs!
  [{:keys [^Datastore datastore]
    :as document-store}
   id-and-docs]
  (->>
    id-and-docs
    (map (fn [[id doc]] (document->entity document-store id doc)))
    ;; 500 is the max per batch operation
    (partition-all 500)
    (run! (fn [batch] (.put datastore ^"[Lcom.google.cloud.datastore.Entity;" (into-array Entity batch))))))

(defn datastore-id->tx-id [^Key datastore-id] (.getId datastore-id))

(defn ^Key tx-id->datastore-id [{:keys [^Datastore datastore namespace]} tx-id]
  (->
    (.newKeyFactory datastore)
    ^KeyFactory (.setKind transaction-kind)
    ^KeyFactory (.setNamespace namespace)
    (.newKey ^long tx-id)))

(defn entity->tx [^Entity entity]
  {::tx/tx-time (.toDate (.getTimestamp entity "created"))
   ::tx/tx-id (datastore-id->tx-id (.getKey entity))
   ::txe/tx-events (blob->clj (.getBlob entity "events"))})

(defn latest-tx [{:keys [^Datastore datastore namespace]}]
  (let [query ^EntityQuery
              (->
                ^EntityQuery$Builder (Query/newEntityQueryBuilder)
                ^EntityQuery$Builder (.setNamespace ^String namespace)
                ^EntityQuery$Builder (.setKind transaction-kind)
                ^EntityQuery$Builder
                (.setOrderBy
                  ^StructuredQuery$OrderBy (StructuredQuery$OrderBy/desc "__key__")
                  ^"[Lcom.google.cloud.datastore.StructuredQuery$OrderBy;" (make-array StructuredQuery$OrderBy 0))
                ^EntityQuery$Builder (.setLimit ^Integer (int 1))
                (.build))]
    (when-let [entity (first (iterator-seq (.run datastore query)))] (entity->tx entity))))

(defn calc-tx-id [{:keys [^Datastore datastore namespace]}]
  (let [query ^KeyQuery
              (->
                ^KeyQuery$Builder (Query/newKeyQueryBuilder)
                ^KeyQuery$Builder (.setNamespace ^String namespace)
                ^KeyQuery$Builder (.setKind transaction-kind)
                ^KeyQuery$Builder
                (.setOrderBy
                  ^StructuredQuery$OrderBy (StructuredQuery$OrderBy/desc "__key__")
                  ^"[Lcom.google.cloud.datastore.StructuredQuery$OrderBy;" (make-array StructuredQuery$OrderBy 0))
                ^KeyQuery$Builder (.setLimit ^Integer (int 1))
                (.build))]
    (if-let [latest-key (first (iterator-seq (.run datastore query)))]
      (inc (datastore-id->tx-id latest-key))
      1)))

(defn ^Entity tx->entity [transaction-log v]
  (let [tx-id (calc-tx-id transaction-log)]
    (->
      (Entity/newBuilder
        ^Key
        (->>
          tx-id
          (tx-id->datastore-id transaction-log)))
      (.set "events" (clj->blob v))
      (.set
        "created"
        (->
          (TimestampValue/newBuilder (Timestamp/now))
          (.setExcludeFromIndexes true)
          (.build)))
      (.build))))

(def max-tries 10)

(defn ^Entity save-tx!
  [{:keys [^Datastore datastore]
    :as transaction-log}
   v]
  (loop [tries 0]
    (let [[type value]
            (try [:entity (.add datastore (tx->entity transaction-log v))] (catch DatastoreException e [:exception e]))]
      (case type
        :entity value
        :exception
          (if-not
            (and (= Code/ALREADY_EXISTS (Code/forNumber (.getCode ^DatastoreException value))) (<= tries max-tries))
            (throw value)
            (do (log/warn "Failed saving tx on try" tries) (recur (inc tries))))))))

(def ^Integer tx-log-batch-size (int 200))

(defn query-seqs [^Datastore datastore ^EntityQuery$Builder q ^Cursor after]
  (lazy-seq
    (let [query ^Query
                (->
                  (.setStartCursor q after)
                  (.build))
          query-results ^QueryResults (.run datastore query)]
      (when-let [entities (doall (iterator-seq query-results))]
        (lazy-seq (cons entities (query-seqs datastore q (.getCursorAfter query-results))))))))

(defn open-tx-log!
  [{:keys [^Datastore datastore namespace]
    :as ds}
   after-tx-id]
  (let [q-builder
          ^EntityQuery$Builder
          (cond->
            (->
              ^EntityQuery$Builder (Query/newEntityQueryBuilder)
              ^EntityQuery$Builder (.setNamespace ^String namespace)
              ^EntityQuery$Builder (.setKind transaction-kind)
              ^EntityQuery$Builder (.setLimit tx-log-batch-size)
              ^EntityQuery$Builder
              (.setOrderBy
                (StructuredQuery$OrderBy/asc "__key__")
                ^"[Lcom.google.cloud.datastore.StructuredQuery$OrderBy;" (make-array StructuredQuery$OrderBy 0)))
            after-tx-id (.setFilter (StructuredQuery$PropertyFilter/gt "__key__" (tx-id->datastore-id ds after-tx-id))))
        query-results ^QueryResults (.run datastore (.build ^EntityQuery$Builder q-builder))
        ;; get-cursor after needs the iterator to be fully realized
        entities (doall (iterator-seq query-results))
        cursor ^Cursor (.getCursorAfter query-results)]
    (cio/->cursor
      (fn [])
      (->>
        (lazy-seq (cons entities (when entities (query-seqs datastore q-builder cursor))))
        (mapcat identity)
        (map entity->tx)))))

(defrecord DatastoreTxLog [datastore bus]
  db/TxLog
    (submit-tx [this tx-events]
      (let [entity (save-tx! this tx-events)
            tx (entity->tx entity)]
        (bus/send bus (merge {:crux/event-type ::submitted-tx} (select-keys tx [::tx/tx-time ::tx/tx-id])))
        (delay tx)))
    (open-tx-log [this after-tx-id] (open-tx-log! this after-tx-id))
    (latest-submitted-tx [this] (latest-tx this)))

(defrecord DatastoreDocumentStore [datastore]
  db/DocumentStore
    (submit-docs [this id-and-docs] (save-docs! this id-and-docs))
    (fetch-docs [this ids] (get-docs-by-ids this ids)))

(s/def ::datastore #(instance? Datastore %))

(def datastore-options
  {::namespace
     {:doc "Datastore namespace"
      :crux.config/required? true
      :crux.config/type :crux.config/string}
   ::datastore
     {:doc "Instance of Datastore"
      :crux.config/required? true
      :crux.config/type ::datastore}})

(def topology
  (merge
    n/base-topology
    {::n/document-store
       {:start-fn
          (fn [_ {::keys [datastore namespace]}]
            (map->DatastoreDocumentStore
              {:datastore datastore
               :namespace namespace}))
        :args datastore-options}
     ::n/tx-log
       {:start-fn
          (fn [{::n/keys [bus]} {::keys [datastore namespace]}]
            (map->DatastoreTxLog
              {:datastore datastore
               :namespace namespace
               :bus bus}))
        :deps [::n/bus]
        :args datastore-options}}))
