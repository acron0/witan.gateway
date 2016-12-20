(ns witan.gateway.protocols)

(defprotocol SendMessage
  (send-message! [this topic message]))

;;;;;;;;;

(defprotocol ManageConnections
  (process-event! [this event])
  (add-receipt! [this cb id])
  (add-connection! [this connection])
  (remove-connection! [this connection]))
;;;;;;;;;

(defprotocol RouteQuery
  (route-query [this payload]))

;;;;;;;;;

(defprotocol Database
  (drop-table! [this table])
  (create-table! [this table columns])
  (insert! [this table row args])
  (select [this table where]))

;;;;;;;;;

(defprotocol Authenticate
  (authenticate [this auth-token]))
