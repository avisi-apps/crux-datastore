(ns avisi.crux.fixtures
  (:require [crux.api :as crux]
            [avisi.crux.datastore :as datastore])
  (:import [com.google.cloud.datastore Datastore DatastoreOptions]
           [com.google.cloud.datastore.testing LocalDatastoreHelper]
           [crux.api ICruxAPI]))

(def ^:dynamic ^Datastore *datastore* nil)
(def ^:dynamic ^LocalDatastoreHelper *datastore-helper* nil)

(defn with-datastore-emulator [f]
  (let [datastore-helper ^LocalDatastoreHelper (LocalDatastoreHelper/create (int 8081))
        options ^DatastoreOptions (.getOptions datastore-helper)]
    (binding [*datastore* (.getService options)
              *datastore-helper* datastore-helper]
      (try
        (.start datastore-helper)
        (f)
        (finally
          (.stop datastore-helper))))))

(def ^:dynamic ^ICruxAPI *api*)
(def ^:dynamic *opts* nil)

(defn with-opts [opts f]
  (binding [*opts* (merge *opts* opts)]
    (f)))

(defn with-datastore [f]
  (with-opts {:crux.node/topology '[avisi.crux.datastore/topology]
              ::datastore/datastore *datastore*
              ::datastore/namespace "test"}
    f))

(defn with-clean-datastore [f]
  (try
    (f)
    (finally
      (.reset *datastore-helper*))))

(defn with-node [f]
  (with-open [node (crux/start-node *opts*)]
    (binding [*api* node]
      (f))))

(defn submit+await-tx
  ([tx-ops] (submit+await-tx *api* tx-ops))
  ([api tx-ops]
   (let [tx (crux/submit-tx api tx-ops)]
     (crux/await-tx api tx)
     tx)))
