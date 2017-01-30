(ns witan.gateway.components.server
  (:gen-class)
  (:require [org.httpkit.server           :as httpkit]
            [ring.middleware.cookies      :refer [wrap-cookies]]
            [ring.middleware.params       :refer [wrap-params]]
            [com.stuartsierra.component   :as component]
            [witan.gateway.handler        :refer [app]]
            [taoensso.timbre              :as log]
            [ring.middleware.cors         :refer [wrap-cors]]))

(defn wrap-log [handler]
  (fn [request]
    (log/info "REQUEST:" request)
    (handler request)))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req :components components))))

(defn wrap-directory
  "Assoc directory to the request."
  [handler directory]
  (fn [req]
    (handler (assoc req :directory directory))))

(defrecord HttpKit [port directory]
  component/Lifecycle
  (start [this]
    (log/info (str "Server started at http://localhost:" port))
    (assoc this :http-kit (httpkit/run-server
                           (-> #'app
                               (wrap-catch-exceptions)
                               (wrap-cookies)
                               (wrap-params)
                               (wrap-directory directory)
                               (wrap-components this)
                               (wrap-log)
                               (wrap-cors :access-control-allow-origin [#".*"]
                                          :access-control-allow-methods [:get :post]))
                           {:port port})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-http-server
  [args directory]
  (->HttpKit (:port args) directory))
