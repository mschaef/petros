(ns petros.handler
  (:use petros.util
        compojure.core
        [slingshot.slingshot :only (throw+ try+)])
  (:require [clojure.tools.logging :as log]            
            [petros.data :as data]
            [petros.core :as core]
            [petros.user :as user]
            [petros.app :as app]
            [cemerick.friend :as friend]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [cemerick.friend.workflows :as workflows]))


(def site-routes
  (routes user/public-routes
          (route/resources "/")
          (friend/wrap-authorize user/private-routes #{:petros.role/verified})
          (friend/wrap-authorize app/app-routes #{:petros.role/verified})
          (route/not-found "Resource Not Found")))

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

(defn user-unauthorized [ request ]
  (core/render-page { :page-title "Access Denied"}
                    [:h1 "Access Denied"]))

(defn user-unverified [ request ]
  (core/render-page { :page-title "E-Mail Unverified"}
                    [:h1 "E-Mail Unverified"]))

(defn missing-verification? [ request ]
  (= (clojure.set/difference (get-in request [:cemerick.friend/authorization-failure
                                              :cemerick.friend/required-roles])
                             (:roles (friend/current-authentication)))
     #{:petros.role/verified}))

(defn unauthorized-handler [request]
  {:status 403
   :body ((if (missing-verification? request)
            user-unverified
            user-unauthorized)
          request)})

(def handler (-> site-routes
                 (friend/authenticate {:credential-fn core/db-credential-fn
                                       :workflows [(workflows/interactive-form)]
                                       :unauthorized-handler unauthorized-handler})
                 (extend-session-duration 1)
                 (wrap-db-connection)
                 (wrap-request-logging)
                 (handler/site)))

