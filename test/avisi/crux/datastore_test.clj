(ns avisi.crux.datastore-test
  (:require
    [clojure.test :as t]
    [avisi.crux.fixtures :as fix :refer [*api*]]
    [avisi.crux.datastore :as datastore]
    [crux.api :as crux]
    [crux.bus :as bus]))

(t/use-fixtures :once fix/with-datastore-emulator)
(t/use-fixtures :each fix/with-clean-datastore fix/with-datastore fix/with-node)

(t/deftest simple-transaction-test
  (t/testing
    "Simple transaction"
    (fix/submit+await-tx
      [[:crux.tx/put
        {:foo "bar"
         :crux.db/id :test}]])
    (t/is
      (crux/entity (crux/db *api*) :test)
      {:foo "bar"
       :crux.db/id :test})))

(t/deftest test-tx-log
  (t/testing
    "Test if tx-log with :with-ops? works"
    (with-open [tx-log-iterator (crux/open-tx-log *api* nil true)]
      (t/is (= (into [] (iterator-seq tx-log-iterator)) [])))
    (let [txes (mapv
                 (fn [i]
                   [[:crux.tx/put
                     {:label (str "label-" i)
                      :crux.db/id (keyword (str "entity-" i))}]])
                 (range 10))
          last-tx (reduce (fn [_ tx] (crux/submit-tx *api* tx)) {} txes)]
      (crux/await-tx *api* last-tx)
      (with-open [tx-log-iterator (crux/open-tx-log *api* nil true)]
        (t/is (= (into [] (map ::crux/tx-ops) (iterator-seq tx-log-iterator)) txes))))))

(t/deftest listening-for-submitted-transactions
  (let [last-event (promise)]
    (bus/listen (:bus *api*) {:crux/event-type ::datastore/submitted-tx} (fn [ev] (deliver last-event ev)))
    (crux/submit-tx
      *api*
      [[:crux.tx/put
        {:foo "bar"
         :crux.db/id :test}]])
    (let [delivered-tx (deref last-event 1000 :timeout)]
      (t/is (not= delivered-tx :timeout))
      (t/is
        (=
          {:crux.tx/tx-id 1
           :crux/event-type ::datastore/submitted-tx}
          (select-keys delivered-tx [:crux.tx/tx-id :crux/event-type]))))))
