(ns witan.gateway.system
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [aero.core :refer [read-config]]
            [kixi.log :as kixi-log]
            [taoensso.timbre :as timbre]
            [kixi.comms :as comms]
            [signal.handler :refer [with-handler]]
            [witan.gateway.protocols :refer [process-event!]]
            [witan.gateway.components.server :refer [new-http-server]]
            [witan.gateway.components.metrics :refer [map->Metrics]]
            [witan.gateway.components.query-router :refer [new-query-router]]
            [witan.gateway.components.connection-manager :refer [new-connection-manager]]
            [witan.gateway.components.auth :refer [new-authenticator]]
            [witan.gateway.components.downloads :refer [new-download-manager]]
            [witan.gateway.components.events :refer [new-event-aggregator]]
            [witan.gateway.components.comms-wrapper :refer [new-comms-wrapper]]))

(defn new-system [profile]
  (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})
        log-config (assoc (:log config)
                          :timestamp-opts kixi-log/default-timestamp-opts)]

    ;; logging config
    (timbre/set-config!
     (assoc log-config
            :appenders (if (or (= profile :staging)
                               (= profile :prod))
                         {:direct-json (kixi-log/timbre-appender-logstash)}
                         {:println (timbre/println-appender)})))

    (comms/set-verbose-logging! (:verbose-logging? config))

    (component/system-map
     :auth        (new-authenticator (-> config :auth))
     :comms       (new-comms-wrapper (-> config :comms :kinesis) (-> config :zk))
     :downloads   (new-download-manager (-> config :downloads) (:directory config))
     :events      (component/using
                   (new-event-aggregator (-> config :events))
                   [:comms])
     :metrics     (component/using
                   (map->Metrics (:metrics config))
                   [])
     :connections (component/using
                   (new-connection-manager (-> config :connections))
                   [:events])
     :queries     (new-query-router (:directory config))
     :http-kit    (component/using
                   (new-http-server (:webserver config) (:directory config))
                   [:connections :comms :queries :auth :downloads :metrics]))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :staging)
        sys (atom nil)]

    ;; https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (timbre/error ex "Unhandled exception:" (.getMessage ex)))))

    (reset! sys (component/start (new-system profile)))
    (with-handler :term
      (timbre/info "SIGTERM was caught: shutting down...")
      (component/stop @sys))))
