(defproject petros "0.1.1-SNAPSHOT"
  :description "Petros - Utility for Automating Plate Count"
  :license { :name "Copyright East Coast Toolworks (c) 2015"}

  :plugins [[lein-ring "0.9.7"]]
  
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [sql-file "0.2.0-pre0"]
                 [clj-http "2.0.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [hiccup "1.0.5"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-json "0.4.0"]
                 [com.cemerick/friend "0.2.1"]
                 [compojure "1.4.0"]
                 [slingshot "0.12.2"]]

  :main petros.main
  :aot [petros.main]
  
  :ring {:handler petros.handler/handler
         :port 8080}
  
  :jar-name "petros.jar"
  :uberjar-name "petros-standalone.jar")
