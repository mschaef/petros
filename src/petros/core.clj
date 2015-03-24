(ns petros.core
  (:require [cemerick.friend :as friend]
            [clojure.string :as string]
            [petros.data :as data]
            [cemerick.friend.credentials :as credentials]))

(defn authenticated-username []
  (if-let [cauth (friend/current-authentication)]
    (cauth :identity)
    nil))

(defn report-unauthorized []
  (friend/throw-unauthorized (friend/current-authentication) {}))

(defn current-user-id []
  ((data/get-user-by-email (authenticated-username)) :user_id))

(defn query-param [ req param-name ]
  (let [params (:params req)]
    (if (nil? params)
      nil
      (params param-name))))

(defn db-credential-fn [ creds ]
  (let [user-record (data/get-user-by-email (creds :username))]
    (if (or (nil? user-record)
            (not (credentials/bcrypt-verify (creds :password) (user-record :password))))
      nil
      {:identity (creds :username)
       :roles #{ :role-user }})))

(defn password-matches? [ password ]
  (not (nil? (db-credential-fn {:username (authenticated-username)
                                :password password}))))
