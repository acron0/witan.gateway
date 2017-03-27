(ns witan.gateway.components.connection-manager
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clj-time.core              :as t]
            [clojure.spec               :as s]
            [kixi.comms                 :as c]
            [witan.gateway.protocols    :as p :refer [ManageConnections]]
            [zookeeper                  :as zk]))

(defonce channels (atom #{}))
(defonce receipts (atom {}))

(defrecord ConnectionManager []
  ManageConnections
  (process-event! [{:keys [receipts]} event]
    (when-let [id (:kixi.comms.command/id event)]
      (try
        (when-let [{:keys [cb]} (get @receipts id)]
          (if-let [error (s/explain-data :kixi.comms.message/event event)]
            (log/error "Event schema coercion failed: " (pr-str error) event)
            (do
              (log/info "Sending event" (:kixi.comms.event/id event) "back to client")
              (cb event)
              nil)))
        (catch Exception e
          (log/error "Error whilst processing an event:" event e)))))
  (add-connection! [{:keys [channels]} connection]
    (swap! channels conj connection)
    (log/info "Added connection. Total:" (count @channels)))
  (remove-connection! [{:keys [channels receipts]} connection]
    (swap! channels #(remove #{connection} %))
    (swap! receipts update (fn [rs]
                             (into {} (remove #(= connection (:ch %)) rs))))
    (log/info "Removed connection. Total:" (count @channels)))
  (add-receipt! [{:keys [receipts]} channel receipt-id callback]
    (swap! receipts assoc (str receipt-id) {:cb callback
                                            :ch channel
                                            :at (t/now)}))

  component/Lifecycle
  (start [{:keys [comms events] :as component}]
    (log/info "Starting Connection Manager")
    (let [c (assoc component
                   :channels  (atom #{})
                   :receipts  (atom {}))
          cmfn (partial p/process-event! c)]
      (p/register-event-receiver! events cmfn)
      (assoc c :cmfn cmfn)))

  (stop [{:keys [comms events] :as component}]
    (log/info "Stopping Connection Manager")
    (p/unregister-event-receiver! events (:cmfn component))
    (dissoc component
            :cmfn
            :channels
            :receipts)))

(defn new-connection-manager [_]
  (map->ConnectionManager {}))
