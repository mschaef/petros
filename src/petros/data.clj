(ns petros.data
  (:use petros.util)
  (:require [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection (sql-file/open-hsqldb-file-conn "petros-db"  "petros" 0))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ var & body ]
  `(binding [ *db* db-connection ]
     ~@body))

;;; user

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

;;; count_sheet

(defn add-count-sheet [ user-id ]
  (:count_sheet_id (first 
                    (jdbc/insert! *db* :count_sheet
                                  {:creator_user_id user-id
                                   :created_on (java.util.Date.)}))))

(defn all-count-sheets [ ]
  (query-all *db*
             [(str "SELECT cs.count_sheet_id, cs.created_on, cs.final_on, u.email_addr"
                   "  FROM count_sheet cs, user u "
                   " WHERE u.user_id = cs.creator_user_id"
                   " ORDER BY cs.created_on DESC")]))
