(ns petros.main
  (:gen-class :main true)
  (:use petros.util)
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [petros.handler :as handler]))

(defn start-webserver [http-port]
  (log/info "Starting Petros Webserver on port" http-port)
  (let [server (jetty/run-jetty handler/handler  {:port http-port :join? false})]
    (add-shutdown-hook
     (fn []
       (log/info "Shutting down webserver")
       (.stop server)))
    (.join server)))

(defn -main [& args]
  (log/info "Starting Petros")
  (start-webserver (config-property "http.port" 8080))
  (log/info "end run."))
