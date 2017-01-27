(ns witan.gateway.system
  (:gen-class)
  (:require [com.stuartsierra.component            :as component]
            [aero.core                             :refer [read-config]]
            [witan.gateway.logstash-appender       :as logstash]
            [taoensso.timbre                       :as timbre]
            ;;
            [witan.gateway.protocols               :refer [process-event!]]
            ;;
            [kixi.comms.components.kafka           :as kafka]
            ;;
            [witan.gateway.components.server       :refer [new-http-server]]
            [witan.gateway.components.query-router :refer [new-query-router]]
            [witan.gateway.components.connection-manager :refer [new-connection-manager]]
            [witan.gateway.components.auth :refer [new-authenticator]]
            [witan.gateway.components.downloads :refer [new-download-manager]]))

(defn new-system [profile]
  (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})]

    ;; logging config
    (timbre/merge-config!
     (assoc (:log config)
            :output-fn (partial logstash/output-fn {:stacktrace-fonts {}})
            :timestamp-opts logstash/logback-timestamp-opts))

    (component/system-map
     :auth        (new-authenticator (-> config :auth))
     :comms       (kafka/map->Kafka (-> config :comms :kafka))
     :downloads   (component/using
                   (new-download-manager (-> config :downloads))
                   [:comms])
     :connections (component/using
                   (new-connection-manager (-> config :connections))
                   [:comms])
     :queries     (new-query-router (:directory config))
     :http-kit    (component/using
                   (new-http-server (:webserver config) (:directory config))
                   [:connections :comms :queries :auth :downloads]))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :production)]

    ;; https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (timbre/error "Unhandled exception:" ex))))

    (component/start
     (new-system profile))))
