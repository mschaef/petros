(ns petros.data
  (:use petros.util)
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection
  (delay (sql-file/open-hsqldb-file-conn (config-property "db.subname" "petros-db")  "petros" 0)))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ & body ]
  `(binding [ *db* @db-connection ]
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
  (let [ name (.trim (or name "")) ]
    (if (= 0 (.length name))
      nil
      (with-transaction
        (or (contributor-id name)
            (add-contributor name))))))

;;; account

(defn all-accounts [ ]
  (query-all *db*
             [(str "SELECT account_id, name"
                   "  FROM account"
                   " ORDER BY account_id")]))

(defn all-account-names [ ]
  (map :name
       (query-all *db*
                  [(str "SELECT name"
                        "  FROM account"
                        " ORDER BY account_id")])))

;;; count_sheet

(defn add-count-sheet [ user-id ]
  (:count_sheet_id (first 
                    (jdbc/insert! *db* :count_sheet
                                  {:creator_user_id user-id
                                   :created_on (java.util.Date.)}))))

(defn all-count-sheets [ ]
  (query-all *db*
             [(str "SELECT cs.count_sheet_id, cs.created_on, cs.final_on, u.email_addr, sum(di.amount) as total_amount"
                   "  FROM (count_sheet cs JOIN user u ON u.user_id = cs.creator_user_id) "
                   "  LEFT JOIN deposit_item di ON cs.count_sheet_id = di.count_sheet_id"
                   " GROUP BY cs.count_sheet_id, cs.created_on, cs.final_on, u.email_addr"
                   " ORDER BY cs.created_on DESC")]))

(defn count-sheet-info [ sheet-id ]
  (query-first *db*
               [(str "SELECT cs.count_sheet_id, cs.created_on, cs.final_on, u.email_addr, sum(di.amount) as total_amount"
                     "  FROM (count_sheet cs JOIN user u ON u.user_id = cs.creator_user_id) "
                     "  LEFT JOIN deposit_item di ON cs.count_sheet_id = di.count_sheet_id"
                     "  WHERE count_sheet_id=?"
                     " GROUP BY cs.count_sheet_id, cs.created_on, cs.final_on, u.email_addr")
                sheet-id]))

(defn all-count-sheet-deposits [ sheet-id ]
  (query-all *db*
             [(str "SELECT c.name as contributor, di.amount, di.notes, di.check_number, acct.name as account_name, acct.account_id as account_id, di.item_id"
                   "  FROM (deposit_item di JOIN account acct ON di.account_id=acct.account_id)"
                   "    LEFT JOIN contributor c ON di.contributor_id=c.contributor_id"
                   " WHERE di.count_sheet_id=?"
                   " ORDER BY di.item_id")
              sheet-id]))

(defn count-sheet-summary [ sheet-id ]
  (map #(assoc % :type (if (= (:type %) 0) :check :cash)) 
       (query-all *db*
                  [(str "SELECT acct.name as account, casewhen(di.check_number is null, 1, 0) as type, sum(di.amount) as total"
                        "  FROM account acct JOIN deposit_item di ON di.account_id=acct.account_id"
                        " WHERE di.count_sheet_id=?"
                        " GROUP BY account, type"
                        " ORDER BY account, type")
                   sheet-id])))

(defn deposit-count-sheet-id [ deposit-id ]
  (query-scalar *db*
                [(str "SELECT count_sheet_id"
                      "  FROM deposit_item"
                      " WHERE item_id=?")
                 deposit-id]))

(defn add-deposit [ sheet-id contributor-name account-id amount check-number notes ]
  (jdbc/insert! *db* :deposit_item
                {:count_sheet_id sheet-id
                 :contributor_id (log/spy :error (intern-contributor contributor-name))
                 :amount amount
                 :check_number check-number
                 :notes notes
                 :account_id account-id}))

(defn update-deposit [ deposit-id contributor-name account-id amount check-number notes ]
  (jdbc/update! *db* :deposit_item
                {:contributor_id (intern-contributor contributor-name)
                 :amount amount
                 :check_number check-number
                 :notes notes
                 :account_id account-id}
                ["item_id=?" deposit-id]))
