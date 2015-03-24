(ns petros.handler
  (:use petros.util
        compojure.core
        [slingshot.slingshot :only (throw+ try+)])
  (:require [clojure.tools.logging :as log]            
            [petros.data :as data]
            [petros.core :as core]
            [petros.user :as user]
            [petros.view :as view]
            [petros.app :as app]
            [cemerick.friend :as friend]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as credentials]))


(defn get-user-id-by-email [ email ]
  (if-let [ user-info (data/get-user-by-email email) ]
    (user-info :user_id)
    nil))

(def site-routes
  (routes user/public-routes
          (route/resources "/")
          (friend/wrap-authorize app/app-routes #{::user})
          (route/not-found "Resource Not Found")))

(defn db-credential-fn [ creds ]
  (let [user-record (data/get-user-by-email (creds :username))]
    (if (or (nil? user-record)
            (not (credentials/bcrypt-verify (creds :password) (user-record :password))))
      nil
      { :identity (creds :username) :roles #{ ::user }})))

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/trace 'REQ (:request-method req) (:uri req))
    (let [t-begin (. System (nanoTime))
          resp (app req)]
      (log/debug 'RESP (:request-method req) (:uri req) (:status resp)
                 (format "(%.1f)" (/ (- (. System (nanoTime)) t-begin) 1000000.0)))
      resp)))

(defn wrap-show-response [ app label ]
  (fn [req]
    (let [resp (app req)]
      (log/trace label (dissoc resp :body))
      resp)))

(defn extend-session-duration [ app duration-in-hours ]
  (fn [req]
    (assoc (app req) :session-cookie-attrs
           {:max-age (* duration-in-hours 3600)})))

(defn wrap-db-connection [ app ]
  (fn [ req ]
    (data/with-db-connection
      (app req))))

(def handler (-> site-routes
                 (friend/authenticate {:credential-fn db-credential-fn
                                       :workflows [(workflows/interactive-form)]})
                 (extend-session-duration 1)
                 (wrap-db-connection)
                 (wrap-request-logging)
                 (handler/site)))

