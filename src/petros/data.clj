(ns petros.data
  (:use petros.util)
  (:require [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection (sql-file/open-hsqldb-file-conn "petros-db"  "petros" 0))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ & body ]
  `(binding [ *db* db-connection ]
     ~@body))

(defmacro with-transaction [ & body ]
  `(jdbc/with-db-transaction [ db-trans# *db* ]
     (binding [ *db* db-trans# ]
       ~@body)))

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

;;; contributor

(defn contributor-id [ name ]
  (query-scalar *db* [(str "SELECT contributor_id "
                           " FROM contributor "
                           " WHERE name=?")
                      name]))

(defn- add-contributor [ name ]
  (:contributor_id (first 
                    (jdbc/insert! *db* :contributor
                                  {:name name}))))

(defn intern-contributor [ name ]
  (with-transaction
    (or (contributor-id name)
        (add-contributor name))))

;;; category

(defn all-categories [ ]
  (query-all *db*
             [(str "SELECT category_id, name"
                   "  FROM category"
                   " ORDER BY category_id")]))

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

(defn all-count-sheet-deposits [ sheet-id ]
  (query-all *db*
             [(str "SELECT c.name, di.amount, di.notes, cat.name as category"
                   "  FROM deposit_item di, contributor c, category cat"
                   " WHERE di.count_sheet_id=?"
                   "   AND di.contributor_id=c.contributor_id"
                   "   AND di.category_id=cat.category_id"
                   " ORDER BY di.item_id DESC")
              sheet-id]))

(defn add-deposit [ sheet-id contributor-name category-id amount notes ]
  (jdbc/insert! *db* :deposit_item
                {:count_sheet_id sheet-id
                 :contributor_id (intern-contributor contributor-name)
                 :amount amount
                 :notes notes
                 :category_id category-id}))
