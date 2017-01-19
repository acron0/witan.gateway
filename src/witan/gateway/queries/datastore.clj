(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn datastore-url
  [s & params]
  (apply str "http://"
         (get-in s [:datastore :host]) ":"
         (get-in s [:datastore :port]) "/"
         (clojure.string/join "/" params)))

(def data-fields
  [:foo :bar])

(defn encode-kw
  [kw]
  (str (namespace kw)
       "_"
       (name kw)))

;; kixi.datastore.metadatastore

(defn metadata-by-id
  [{:keys [kixi.user/id kixi.user/groups]} system-map meta-id]
  (let [url (datastore-url system-map "metadata" meta-id)
        resp @(http/get url {:headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (json/parse-string % keyword))))
      {:error (str "invalid status: " (:status resp))})))

(defn metadata-with-activities
  "List file metadata with *this* activities set."
  [{:keys [kixi.user/id kixi.user/groups]} d activities]
  (let [url (datastore-url d "metadata")
        resp @(http/get url {:query-params (merge {:activity
                                                   (mapv encode-kw activities)}
                                                  #_(when index
                                                      {:index index})
                                                  #_(when count
                                                      {:count count}))
                             :headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (:body (update resp
                   :body
                   #(when %
                      (json/parse-string % keyword))))))
