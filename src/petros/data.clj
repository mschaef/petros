(ns petros.data
  (:use petros.util)
  (:require [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection (sql-file/open-hsqldb-file-conn "petros-db"  "petros" 0))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ var & body ]
  `(binding [ *db* db-connection ]
     ~@body))

(defn all-user-names [ ]
  (map :name (query-all *db* ["select name from user order by name"])))

(defn get-user-by-email [ email-addr ]
  (query-first *db* ["select * from user where email_addr=?" email-addr]))

(defn user-email-exists? [ email-addr ]
  (not (nil? (get-user-by-email email-addr))))

(defn get-user-by-id [ user-id ]
  (query-first *db* ["select * from user where user_id=?" user-id]))

(defn add-user [ email-addr password ]
  (:user_id (first
             (jdbc/insert! *db*
              :user
              {:email_addr email-addr
               :password password}))))

