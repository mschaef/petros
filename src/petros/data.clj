(ns petros.data
  (:use petros.util
        sql-file.sql-util)
  (:import)
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection
  (delay (-> (sql-file/open-pool {:name (config-property "db.subname" "petros-db")
                                  :schema-path ["sql/"]})
             (sql-file/ensure-schema ["petros" 0]))))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [& body]
  `(binding [*db* @db-connection]
     ~@body))

(defmacro with-transaction [& body]
  `(jdbc/with-db-transaction [db-trans# *db*]
     (binding [*db* db-trans#]
       ~@body)))

;;; user

(defn all-user-names []
  (map :name (query-all *db* ["select name from user order by name"])))

(defn get-user-roles [user-id]
  (set
   (map #(keyword "petros.role" (:role_name %))
        (query-all *db*
                   [(str "SELECT role_name"
                         "  FROM user u, role r, user_role ur"
                         "  WHERE u.user_id = ur.user_id"
                         "    AND ur.role_id = r.role_id"
                         "    AND u.user_id = ?")
                    user-id]))))

(defn- get-role-id [role-name]
  (query-scalar *db*
                [(str "SELECT role_id"
                      "  FROM role"
                      " WHERE role_name = ?")
                 (name role-name)]))

(defn delete-user-roles [user-id]
  (jdbc/delete! *db* :user_role ["user_id=?" user-id]))

(defn set-user-roles [user-id role-set]
  (with-transaction
    (delete-user-roles user-id)
    (doseq [role-id (map get-role-id role-set)]
      (jdbc/insert! *db* :user_role
                    {:user_id user-id
                     :role_id role-id}))))

(defn add-user [email-addr password]
  (:user_id (first
             (jdbc/insert! *db*
                           :user
                           {:email_addr email-addr
                            :password password}))))

(defn set-user-password [email-addr password]
  (jdbc/update! *db* :user
                {:password password}
                ["email_addr=?" email-addr]))

(defn get-user-by-id [user-id]
  (query-first *db* [(str "SELECT *"
                          "  FROM user"
                          " WHERE user_id=?")
                     user-id]))

(defn get-user-by-email [email-addr]
  (query-first *db* [(str "SELECT *"
                          "  FROM user"
                          " WHERE email_addr=?")
                     email-addr]))

(defn user-email-exists? [email-addr]
  (not (nil? (get-user-by-email email-addr))))

(defn create-verification-link [user-id]
  (:verification_link_id
   (first
    (jdbc/insert! *db* :verification_link
                  {:link_uuid (.toString (java.util.UUID/randomUUID))
                   :verifies_user_id user-id
                   :created_on (java.util.Date.)}))))

(defn get-verification-link-by-id [link-id]
  (query-first *db* [(str "SELECT *"
                          "  FROM verification_link"
                          " WHERE verification_link_id=?")
                     link-id]))

(defn get-verification-link-by-uuid [link-uuid]
  (query-first *db* [(str "SELECT *"
                          "  FROM verification_link"
                          " WHERE link_uuid=?")
                     link-uuid]))

;;; contributor

(defn contributor-id [name]
  (query-scalar *db* [(str "SELECT contributor_id "
                           " FROM contributor "
                           " WHERE name=?")
                      name]))

(defn- add-contributor [name]
  (:contributor_id (first
                    (jdbc/insert! *db* :contributor
                                  {:name name}))))

(defn intern-contributor [name]
  (let [name (.trim (or name ""))]
    (if (= 0 (.length name))
      nil
      (with-transaction
        (or (contributor-id name)
            (add-contributor name))))))

;;; account

(defn all-accounts []
  (query-all *db*
             [(str "SELECT account_id, name"
                   "  FROM account"
                   " ORDER BY account_id")]))

(defn all-account-names []
  (map :name
       (query-all *db*
                  [(str "SELECT name"
                        "  FROM account"
                        " ORDER BY account_id")])))

;;; count_sheet

(defn add-count-sheet [user-id]
  (:count_sheet_id (first
                    (jdbc/insert! *db* :count_sheet
                                  {:creator_user_id user-id
                                   :created_on (java.util.Date.)}))))

(defn all-count-sheets []
  (query-all *db*
             [(str "SELECT cs.count_sheet_id, cs.created_on, cs.creator_user_id, cs.final_on, cs.finalizer_user_id, u.email_addr, sum(di.amount) as total_amount"
                   "  FROM (count_sheet cs JOIN user u ON u.user_id = cs.creator_user_id) "
                   "  LEFT JOIN deposit_item di ON cs.count_sheet_id = di.count_sheet_id"
                   " GROUP BY cs.count_sheet_id, cs.created_on, cs.creator_user_id, cs.final_on, cs.finalizer_user_id, u.email_addr"
                   " ORDER BY cs.created_on DESC")]))

(defn count-sheet-info [sheet-id]
  (query-first *db*
               [(str "SELECT cs.count_sheet_id, cs.created_on, cs.creator_user_id, cs.final_on, cs.finalizer_user_id, u.email_addr, sum(di.amount) as total_amount"
                     "  FROM (count_sheet cs JOIN user u ON u.user_id = cs.creator_user_id) "
                     "  LEFT JOIN deposit_item di ON cs.count_sheet_id = di.count_sheet_id"
                     "  WHERE count_sheet_id=?"
                     " GROUP BY cs.count_sheet_id, cs.created_on, cs.creator_user_id, cs.final_on, cs.finalizer_user_id, u.email_addr")
                sheet-id]))

(defn all-count-sheet-deposits [sheet-id]
  (query-all *db*
             [(str "SELECT c.name as contributor, di.amount, di.notes, di.check_number, acct.name as account_name, acct.account_id as account_id, di.item_id"
                   "  FROM (deposit_item di JOIN account acct ON di.account_id=acct.account_id)"
                   "    LEFT JOIN contributor c ON di.contributor_id=c.contributor_id"
                   " WHERE di.count_sheet_id=?"
                   " ORDER BY di.item_id")
              sheet-id]))

(defn all-count-sheet-deposits-with-notes [sheet-id]
  (query-all *db*
             [(str "SELECT c.name as contributor, di.amount, di.notes, di.check_number, acct.name as account_name, acct.account_id as account_id, di.item_id"
                   "  FROM (deposit_item di JOIN account acct ON di.account_id=acct.account_id)"
                   "    LEFT JOIN contributor c ON di.contributor_id=c.contributor_id"
                   " WHERE di.count_sheet_id=?"
                   "   AND LENGTH(di.notes) > 0"
                   " ORDER BY di.item_id")
              sheet-id]))

(defn count-sheet-summary [sheet-id]
  (map #(assoc % :type (if (= (:type %) 0) :check :cash))
       (query-all *db*
                  [(str "SELECT acct.name as account, casewhen(di.check_number is null, 1, 0) as type, sum(di.amount) as total"
                        "  FROM account acct JOIN deposit_item di ON di.account_id=acct.account_id"
                        " WHERE di.count_sheet_id=?"
                        " GROUP BY account, type"
                        " ORDER BY account, type")
                   sheet-id])))

(defn sheet-deposits-by-contributor [sheet-id]
  (query-all *db*
             [(str "SELECT contributor_id, ci.name as name, sum(di.amount) as total"
                   "  FROM deposit_item di JOIN contributor ci ON di.contributor_id=ci.contributor_id"
                   " WHERE di.count_sheet_id=?"
                   " GROUP BY ci.name, ci.contributor_id, di.contributor_id"
                   " ORDER BY ci.name")
              sheet-id]))

(defn deposit-count-sheet-id [deposit-id]
  (query-scalar *db*
                [(str "SELECT count_sheet_id"
                      "  FROM deposit_item"
                      " WHERE item_id=?")
                 deposit-id]))

(defn add-deposit [sheet-id contributor-name account-id amount check-number notes]
  (jdbc/insert! *db* :deposit_item
                {:count_sheet_id sheet-id
                 :contributor_id (log/spy :error (intern-contributor contributor-name))
                 :amount amount
                 :check_number check-number
                 :notes notes
                 :account_id account-id}))

(defn update-deposit [deposit-id contributor-name account-id amount check-number notes]
  (jdbc/update! *db* :deposit_item
                {:contributor_id (intern-contributor contributor-name)
                 :amount amount
                 :check_number check-number
                 :notes notes
                 :account_id account-id}
                ["item_id=?" deposit-id]))

(defn delete-deposits-from-sheet [sheet-id deposit-ids]
  (doseq [deposit-id deposit-ids]
    (jdbc/delete! *db* :deposit_item
                  ["item_id=? AND count_sheet_id=?" deposit-id sheet-id])))

(defn finalize-sheet [sheet-id user-id]
  (jdbc/update! *db* :count_sheet
                {:final_on (java.util.Date.)
                 :finalizer_user_id user-id}
                ["count_sheet_id=?" sheet-id]))
