(ns petros.app
  (:use petros.util
        compojure.core)
  (:require [clojure.tools.logging :as log]            
            [petros.data :as data]
            [petros.core :as core]
            [petros.user :as user]
            [compojure.route :as route]
            [hiccup.form :as form]
            [compojure.handler :as handler]
            [ring.util.response :as ring]))

(def icon-check  [:i {:class "fa fa-check fa-lg icon-check"}])

(defn user-has-access-to-sheet? [ user-id sheet-info ]
  (let [{creator-id :creator_user_id
         finalizer-id :finalizer_user_id} sheet-info]
    (or (= user-id creator-id)
        (= user-id finalizer-id))))

(defn user-has-access-to-sheet-by-id? [ user-id sheet-id ]
  (user-has-access-to-sheet? user-id (data/count-sheet-info sheet-id)))

(defn current-user-has-access-to-sheet? [ sheet-id ]
  (user-has-access-to-sheet-by-id? (core/current-user-id) sheet-id))

(defn ensure-bigdec [ val ]
  (if (= (.getClass val) java.math.BigDecimal)
    val
    (java.math.BigDecimal. val)))

(defn formatter [ fmt-fn default-default ]
  (fn fmt
    ([ amount ]
       (fmt amount default-default))
    ([ amount default ]
       (cond
        (string? amount) amount
        (nil? amount) (fmt default)
        :else (fmt-fn amount)))))

(defn fmt-ccy-amount [ amount ]
  (let [ amount (ensure-bigdec amount) ]
    (if (< (.compareTo (.abs amount) 0.001M) 0)
      "-"
      (format "$%.2f" amount))))
  
(def fmt-ccy (formatter fmt-ccy-amount 0))

(defn fmt-ccy-amount-0 [ amount ]
  (format "$%.2f"(ensure-bigdec amount)))
  
(def fmt-ccy-0 (formatter fmt-ccy-amount-0 0))

(def fmt-date (formatter #(format "%1$tB %1$te, %1$tY" %) ""))

(defn table-head [ & tds ]
    (let [ [ attrs tds ]
         (if (map? tds)
           [ (first tds) (rest tds) ]
           [ {} tds ])]
      `[:thead
        [:tr ~attrs ~@(map (fn [ td ] (unless (nil? td) [:th td])) tds)]]))

(defn table-row [ & tds ]
  (let [ [ attrs tds ]
         (if (map? (first tds))
           [ (first tds) (rest tds) ]
           [ {} tds ])]
    `[:tr ~attrs ~@(map (fn [ td ] [:td td]) tds)]))

(defn sheet-url [ sheet-id ]
  (str "/sheet/" sheet-id))

(defn sheet-summary-url [ sheet-id ]
  (str "/sheet/" sheet-id "/summary"))

(defn sheet-checks-url [ sheet-id ]
  (str "/sheet/" sheet-id "/checks"))

(defn sheet-printable-url [ sheet-id ]
  (str "/sheet/" sheet-id "/printable"))


(defn render-sheet-list []
  (let [ user-id (core/current-user-id) ]
    (core/render-page {:page-title "Count Sheets" }
                      [:table.data
                       (table-head "Creator" "Created On" "Final On" "Total Amount" "")
                       [:tr
                        [:td { :colspan 5}
                         (form/form-to [:post "/"]
                                       [:input {:type "submit"
                                                :value "Create a New Count Sheet"}])]]
                       (map #(let [ id (:count_sheet_id %) ]
                               (table-row (:email_addr %)
                                          (fmt-date (:created_on %))
                                          (fmt-date (:final_on %))
                                          (fmt-ccy (:total_amount %))
                                          [:a { :href (sheet-url id) }
                                           (if (nil? (:final_on %)) "Edit" "View")]))
                            (filter #(user-has-access-to-sheet? user-id %)
                                    (data/all-count-sheets)))])))

(defn account-selector [ attrs id val ]
  [:select (merge attrs { :name id })
   (map (fn [ info ]
          [:option (assoc-if { :value (:account_id info) }
                             (if-let [ id (parsable-integer? val)]
                               (== (:account_id info) id)
                               false)
                             :selected ())
           (:name info)])
        (data/all-accounts))])

(defn active-classes [ active? ]
  (class-set {"is-active" active? "is-inactive" (not active?)}))

(defn render-sheet-sidebar [ id mode ]
  (let [info (data/count-sheet-info id)]
    [:div.content
     [:div.total
      (fmt-ccy-0 (:total_amount info))]
     [:div.entry
      (fmt-date (:created_on info))]
     [:div.entry
      (:email_addr info)]
     [:div.vspace]     
     [:div.menu-entry {:class (active-classes (= mode :entry))}
      [:a { :href (sheet-url id)} "Contributions"]]
     [:div.menu-entry {:class (active-classes (= mode :summary))}
      [:a { :href (sheet-summary-url id)} "Sheet Summary"]]
     [:div.menu-entry {:class (active-classes (= mode :checks))}
      [:a { :href (sheet-checks-url id)} "Checks"]]     
     [:div.vspace]
     [:div.menu-entry.center { }
      (if (nil? (:final_on info))
        [:input {:id "finalize_sheet"
                 :type "submit"
                 :value "Finalize Sheet"}]
        [:a { :href (sheet-printable-url id) :target "_blank" } "Printable"])]     
     [:div.vspace]
     [:div.entry
      [:a { :href "/"} "Home"]]]))

(defn group-summary [ summary ]
  (reduce (fn [ out s-entry ]
            (assoc-in out [ (:account s-entry) (:type s-entry) ] (:total s-entry)))
          {}
          summary))

(defn total-amounts [ summary ]
  (reduce (fn [ total s-entry ]
            (+ total (:total s-entry)))
          0
          summary))

(defn sheet-summary-list [ id ]
  (let [summary (data/count-sheet-summary id)
        summary-data (group-summary summary)]
     [:table.data.summary
      (table-head "Account" "Check" "Cash" "Subtotal")
      (map (fn [ acct-name ]
             [:tr
              [:td acct-name]
              [:td.value (fmt-ccy (get-in summary-data [ acct-name :check ]) 0.0)]
              [:td.value (fmt-ccy (get-in summary-data [ acct-name :cash ]) 0.0)]
              [:td.value (fmt-ccy (+ (get-in summary-data [ acct-name :check ] 0.0)
                                     (get-in summary-data [ acct-name :cash ] 0.0)))]])
           (data/all-account-names))
      [:tr
       [:td "Total"]
       [:td.value (fmt-ccy (total-amounts (filter #(= :check (:type %)) summary)))]
       [:td.value (fmt-ccy (total-amounts (filter #(= :cash (:type %)) summary)))]
       [:td.value  (fmt-ccy (total-amounts summary))]]]))

(defn render-sheet-summary [id error-msg init-vals ]
  (let [info (data/count-sheet-info id)]
    (core/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)) )
                       :sidebar (render-sheet-sidebar id :summary)}
                      [:h1 "Summary"]
                      (sheet-summary-list id))))


(defn sheet-check-list [ id ]
  [:table.data.checks.full-width
   [:thead
    [:tr
     [:th "Contributor"]
     [:th "Check Number"]
     [:th "Amount"]
     [:th "Account"]
     [:th.notes "Notes"]]]
   
   (let [ checks (filter :check_number
                         (data/all-count-sheet-deposits id))]
     (if (> (count checks) 0)
       (map (fn [ check ]
              [:tr
               [:td (:contributor check)]
               [:td.value (or (:check_number check) "Cash")]
               [:td.value (fmt-ccy (:amount check))]
               [:td.value (:account_name check)]
               [:td (:notes check)]])
            checks)
       [:tr [:td.no-checks { :colspan "5" } "No Checks"]]))])

(defn render-sheet-checks [id error-msg init-vals ]
  (let [info (data/count-sheet-info id)]
    (core/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)) )
                       :sidebar (render-sheet-sidebar id :checks)}
                    
                      [:h1 "Checks"]
                      (sheet-check-list id))))

(defn render-printable-sheet [ sheet-id ]
  (let [info (data/count-sheet-info sheet-id)]
    (core/render-printable (str "Count Sheet - " (fmt-date (:created_on info)) )
                           [:h1 "Summary"]
                           (sheet-summary-list sheet-id)
                           [:h1 "Checks"]
                           (sheet-check-list sheet-id))))

(defn item-select-checkbox [ item-id ]
  [:input {:type "checkbox" :class "item-select" :name (str "item_" item-id)}])

(defn item-edit-row [ sheet-id editable? error-msg init-vals post-target cancel-target]
  (list
   (form/form-to { } [:post post-target]
                 [:tr
                  (when editable?
                    [:td
                     (if-let [ item-id (:item_id init-vals) ]
                       (item-select-checkbox (:item_id init-vals)))])
                  [:td (form/text-field { } "contributor" (:contributor init-vals))]
                  [:td (form/text-field { } "check_number" (:check_number init-vals))]
                  [:td (form/text-field { } "amount" (:amount init-vals))]
                  [:td (account-selector { } "account_id" (:account_id init-vals))]
                  [:td
                   (form/text-field { :style "width:100%"} "notes" (:notes init-vals))
                   [:button.hidden-submit { :type "submit" } icon-check ]]])
   (when error-msg
     [:tr [:td {:class "error-message" :colspan "85"} error-msg]])))

(defn item-display-row [ sheet-id editable? dep-item ]
  [:tr { :class "clickable-row" :data-href (str "/sheet/" sheet-id "?edit-item=" (:item_id dep-item))}
   (when editable?
     [:td (item-select-checkbox (:item_id dep-item))])
   [:td.value (or (:contributor dep-item) [:span.informational "Unattributed"])]
   [:td.value (or (:check_number dep-item) [:span.informational "Cash"])]
   [:td.value (fmt-ccy (:amount dep-item))]
   [:td.value (:account_name dep-item)]
   [:td (:notes dep-item)]])

(defn render-sheet [ sheet-id error-msg init-vals edit-item ]
  (let [info (data/count-sheet-info sheet-id)
        editable? (nil? (:final_on info))]
    (log/error info editable?)
    (core/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)))
                       :include-js [ "/petros-sheet.js" ]
                       :sidebar (render-sheet-sidebar sheet-id :entry)}
                      [:table.data.entries.full-width
                       (table-head (when editable? "")  "Contributor" "Check Number" "Amount" "Account" "Notes")
                       (map #(if (and editable?
                                      (parsable-integer? edit-item)
                                      (== (:item_id %) (parsable-integer? edit-item)))
                               (item-edit-row sheet-id editable? error-msg % (str "/item/" (:item_id %))  (str "/sheet/" sheet-id))
                               (item-display-row sheet-id editable? %))
                            (data/all-count-sheet-deposits sheet-id))
                       (when editable?
                         (list (if edit-item
                                 [:tr { :class "clickable-row edit-row" :data-href (str "/sheet/" sheet-id )}
                                  [:td {:colspan "6"}
                                   [:a {:href (sheet-url sheet-id)}
                                    "Add new item..."]]]
                                 (item-edit-row sheet-id editable? error-msg init-vals (str "/sheet/" sheet-id) (str "/sheet/" sheet-id)))
                               [:input {:id "delete_entries" :type "submit" :value "Delete Selected Entries"}]))]
                      [:div.help
                       [:p
                        [:span.label "Contributor"] " - "
                        "Leave this blank for cash or other contributions without an attribution."]
                       [:p
                        [:span.label "Check Number"] " - "
                        "Leave this blank for cash contributions."]]
                      [:input#sheet-id { :type "hidden" :name "sheet-id" :value sheet-id}])))


(defn accept-integer [ obj message ]
  (or (integer? obj)
      (parsable-integer? obj)
      (fail-validation message)))

(defn accept-amount [ obj message ]
  (if-let [ amt (parsable-double? obj) ]
    (if (>= amt 0.0)
      amt
      (fail-validation message))
    (fail-validation message)))

(defn accept-check-number [ obj message ]
  (if (string-empty? obj) 
    nil
    (or (parsable-integer? obj) 
        (fail-validation message))))

(defn accept-notes [ obj message ] 
  obj)


(defroutes app-routes
  (GET "/" []
    (render-sheet-list))
  
  (POST "/" []
    (let [ user-id (core/current-user-id) ]
      (log/info "Adding count-sheet: " user-id)
      (let [ sheet-id (data/add-count-sheet (core/current-user-id))]
        (ring/redirect (sheet-url sheet-id)))))

  (GET "/sheet/:sheet-id" { { sheet-id :sheet-id edit-item :edit-item last-account-id :last_account_id } :params }
       (when (current-user-has-access-to-sheet? sheet-id)
         (log/info "Displaying sheet: " sheet-id)
         (render-sheet sheet-id nil { :account_id last-account-id} edit-item)))

  (POST "/sheet/:sheet-id/delete-items" { { sheet-id :sheet-id } :params
                                          item-ids :body}
    (log/info "Deleting from sheet: " sheet-id  ", body: " item-ids)
    (when (current-user-has-access-to-sheet? sheet-id)
      (data/delete-deposits-from-sheet sheet-id item-ids)
      "Success"))

  (POST "/sheet/:sheet-id/finalize" { { sheet-id :sheet-id } :params }
    (log/info "Finalizing sheet: " sheet-id)
    (when (current-user-has-access-to-sheet? sheet-id)
      (data/finalize-sheet sheet-id (core/current-user-id))
      "Success"))
    
  (GET "/sheet/:sheet-id/summary" [ sheet-id ]
       (log/info "Displaying sheet summary: " sheet-id)
       (when (current-user-has-access-to-sheet? sheet-id)
         (render-sheet-summary sheet-id nil {})))

  (GET "/sheet/:sheet-id/printable" [ sheet-id ]
       (log/info "Displaying printable sheet: " sheet-id)
       (when (current-user-has-access-to-sheet? sheet-id)
         (render-printable-sheet sheet-id)))

  (GET "/sheet/:sheet-id/checks" [ sheet-id ]
       (log/info "Displaying sheet checks: " sheet-id)
       (when (current-user-has-access-to-sheet? sheet-id)
         (render-sheet-checks sheet-id nil {})))

  (POST "/item/:item-id" { params :params }
    (let [ {item-id :item-id 
            account_id :account_id
            contributor :contributor
            amount :amount
            check-number :check_number
            notes :notes} params
            sheet-id (data/deposit-count-sheet-id item-id)]
      (log/info "Updating deposit: " params)
      (with-validation #(render-sheet sheet-id % params item-id)
        (data/update-deposit (accept-integer item-id           "Invalid item-id")
                             contributor
                             (accept-integer account_id        "Invalid account")
                             (accept-amount amount             "Invalid amount")
                             (accept-check-number check-number "Invalid check number")
                             (accept-notes notes               "Invalid notes")) 
        (ring/redirect (sheet-url sheet-id)))))

  (POST "/sheet/:sheet-id" { params :params }
    (let [ {sheet-id :sheet-id 
            account_id :account_id
            contributor :contributor
            amount :amount
            check-number :check_number
            notes :notes} params ]
      (log/info "Adding deposit: " params)
      (with-validation #(render-sheet sheet-id % params nil)
        (data/add-deposit (accept-integer sheet-id          "Invalid sheet-id")
                          contributor
                          (accept-integer account_id       "Invalid account")
                          (accept-amount amount             "Invalid amount")
                          (accept-check-number check-number "Invalid check number")
                          (accept-notes notes               "Invalid notes")) 
        (ring/redirect (str (sheet-url sheet-id) "?last_account_id=" account_id))))))


