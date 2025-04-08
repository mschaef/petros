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

(defn user-has-access-to-sheet? [user-id sheet-info]
  (let [{creator-id :creator_user_id
         finalizer-id :finalizer_user_id} sheet-info]
    (or (= user-id creator-id)
        (= user-id finalizer-id))))

(defn user-has-access-to-sheet-by-id? [user-id sheet-id]
  (user-has-access-to-sheet? user-id (data/count-sheet-info sheet-id)))

(defn current-user-has-access-to-sheet? [sheet-id]
  (user-has-access-to-sheet-by-id? (core/current-user-id) sheet-id))

(defn ensure-bigdec [val]
  (if (= (.getClass val) java.math.BigDecimal)
    val
    (java.math.BigDecimal. val)))

(defn formatter [fmt-fn default-default]
  (fn fmt
    ([amount]
     (fmt amount default-default))
    ([amount default]
     (cond
       (string? amount) amount
       (nil? amount) (fmt default)
       :else (fmt-fn amount)))))

(defn fmt-ccy-amount [amount]
  (let [amount (ensure-bigdec amount)]
    (if (< (.compareTo (.abs amount) 0.001M) 0)
      "-"
      (format "$%.2f" amount))))

(def fmt-ccy (formatter fmt-ccy-amount 0))

(defn fmt-ccy-amount-0 [amount]
  (format "$%.2f" (ensure-bigdec amount)))

(def fmt-ccy-0 (formatter fmt-ccy-amount-0 0))

(def fmt-date (formatter #(format "%1$tB %1$te, %1$tY" %) ""))

(defn table-head [& tds]
  (let [[attrs tds]
        (if (map? tds)
          [(first tds) (rest tds)]
          [{} tds])]
    `[:thead
      [:tr ~attrs ~@(map (fn [td] (unless (nil? td) [:th td])) tds)]]))

(defn table-row [& tds]
  (let [[attrs tds]
        (if (map? (first tds))
          [(first tds) (rest tds)]
          [{} tds])]
    `[:tr ~attrs ~@(map (fn [td] [:td td]) tds)]))

(defn sheet-url [sheet-id]
  (str "/sheet/" sheet-id))

(defn sheet-summary-url [sheet-id]
  (str "/sheet/" sheet-id "/summary"))

(defn sheet-checks-url [sheet-id]
  (str "/sheet/" sheet-id "/checks"))

(defn sheet-printable-url [sheet-id]
  (str "/sheet/" sheet-id "/printable"))

(defn render-list-sidebar []
  [:div.content
   [:div.control
    (form/form-to [:post "/"]
                  [:input.command {:type "submit"
                                   :value "Create Count Sheet"}])]])

(defn render-sheet-list []
  (let [user-id (core/current-user-id)]
    (core/render-page {:page-title "Count Sheets"
                       :sidebar (render-list-sidebar)}
                      [:table.data.sheets
                       (table-head "Creator" "Created On" "Final On" "Total Amount" "")
                       (map #(let [id (:count_sheet_id %)]
                               (table-row (:email_addr %)
                                          (fmt-date (:created_on %))
                                          (fmt-date (:final_on %))
                                          (fmt-ccy (:total_amount %))
                                          [:a {:href (sheet-url id)}
                                           (if (nil? (:final_on %)) "Edit" "View")]))
                            (filter #(user-has-access-to-sheet? user-id %)
                                    (data/all-count-sheets)))])))

(defn account-selector [attrs id val]
  [:select (merge attrs {:name id})
   (map (fn [info]
          [:option (assoc-if {:value (:account_id info)}
                             (if-let [id (parsable-integer? val)]
                               (== (:account_id info) id)
                               false)
                             :selected ())
           (:name info)])
        (data/all-accounts))])

(defn active-classes [active?]
  (class-set {"is-active" active? "is-inactive" (not active?)}))

(defn render-sheet-sidebar [id mode]
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
      [:a {:href (sheet-url id)} "Contributions"]]
     [:div.menu-entry {:class (active-classes (= mode :summary))}
      [:a {:href (sheet-summary-url id)} "Sheet Summary"]]
     [:div.menu-entry {:class (active-classes (= mode :checks))}
      [:a {:href (sheet-checks-url id)} "Checks"]]]))

(defn group-summary [summary]
  (reduce (fn [out s-entry]
            (assoc-in out [(:account s-entry) (:type s-entry)] (:total s-entry)))
          {}
          summary))

(defn total-amounts [summary]
  (reduce (fn [total s-entry]
            (+ total (:total s-entry)))
          0
          summary))

(defn sheet-summary-list [sheet-id]
  (let [summary (data/count-sheet-summary sheet-id)
        summary-data (group-summary summary)]
    [:table.data.summary.full-width
     (table-head "Account" "Check" "Cash" "Subtotal")
     (map (fn [acct-name]
            [:tr
             [:td acct-name]
             [:td.value (fmt-ccy (get-in summary-data [acct-name :check]) 0.0)]
             [:td.value (fmt-ccy (get-in summary-data [acct-name :cash]) 0.0)]
             [:td.value.summary (fmt-ccy (+ (get-in summary-data [acct-name :check] 0.0)
                                            (get-in summary-data [acct-name :cash] 0.0)))]])
          (data/all-account-names))
     [:tr.summary
      [:td "Total"]
      [:td.value (fmt-ccy (total-amounts (filter #(= :check (:type %)) summary)))]
      [:td.value (fmt-ccy (total-amounts (filter #(= :cash (:type %)) summary)))]
      [:td.value  (fmt-ccy (total-amounts summary))]]]))

(defn sheet-note-list [sheet-id]
  [:table.data.notes.full-width
   [:thead
    [:tr
     [:th "Contributor"]
     [:th "Check Number"]
     [:th "Amount"]
     [:th "Account"]
     [:th.notes "Notes"]]]

   (let [notes (data/all-count-sheet-deposits-with-notes sheet-id)]
     (if (> (count notes) 0)
       (map (fn [note]
              [:tr
               [:td (:contributor note)]
               [:td.value (or (:check_number note) "Cash")]
               [:td.value (fmt-ccy (:amount note))]
               [:td.value (:account_name note)]
               [:td (:notes note)]])
            notes)
       [:tr [:td.no-notes {:colspan "5"} "No deposits with notes"]]))])

(defn render-sheet-summary [id error-msg init-vals]
  (let [info (data/count-sheet-info id)]
    (core/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)))
                       :sidebar (render-sheet-sidebar id :summary)}
                      [:h1 "Summary"]
                      (sheet-summary-list id))))

(defn sheet-check-list [sheet-id]
  [:table.data.checks.full-width
   [:thead
    [:tr
     [:th "Contributor"]
     [:th "Check Number"]
     [:th "Amount"]
     [:th "Account"]
     [:th.notes "Notes"]]]

   (let [checks (filter :check_number (data/all-count-sheet-deposits sheet-id))]
     (if (> (count checks) 0)
       (map (fn [check]
              [:tr
               [:td (:contributor check)]
               [:td.value (or (:check_number check) "Cash")]
               [:td.value (fmt-ccy (:amount check))]
               [:td.value (:account_name check)]
               [:td (:notes check)]])
            checks)
       [:tr [:td.no-checks {:colspan "5"} "No Checks"]]))])

(defn sheet-check-deposit-sheet [sheet-id]
  [:table.data.checks
   [:thead
    [:tr
     [:th "Contributor"]
     [:th "Check Number"]
     [:th "Amount"]]]

   (let [checks (filter :check_number (data/all-count-sheet-deposits sheet-id))]
     (if (> (count checks) 0)
       (list
        (map (fn [check]
               [:tr
                [:td.value (:contributor check)]
                [:td.value (or (:check_number check) "Cash")]
                [:td.value (fmt-ccy (:amount check))]])
             checks)
        [:tr.summary
         [:td {:colspan 2} "Total"]
         [:td.value (fmt-ccy (apply + (map :amount checks)))]])
       [:tr [:td.no-checks {:colspan "3"} "No Checks"]]))])

(defn sheet-contributor-report-row [dep-item]
  [:tr
   [:td.value (or (:contributor dep-item) "Plate")]
   [:td.value (or (:check_number dep-item) "Cash")]
   [:td.value (fmt-ccy (:amount dep-item))]
   [:td.value (:account_name dep-item)]
   [:td (:notes dep-item)]])

(defn sheet-contributor-report [sheet-id]
  [:table.data.checks
   (table-head  "Contributor" "Check Number" "Amount" "Account" "Notes")
   (map sheet-contributor-report-row
        (data/all-count-sheet-deposits sheet-id))])

(defn render-sheet-checks [id error-msg init-vals]
  (let [info (data/count-sheet-info id)]
    (core/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)))
                       :sidebar (render-sheet-sidebar id :checks)}

                      [:h1 "Checks"]
                      (sheet-check-list id))))

(defn report-page [title & body]
  [:div.page
   [:h1 title]
   body])

(defn render-report-header [info]
  [:div.report-header
   [:div.entry
    [:span.label "Counter"]
    (:email_addr info)]
   [:div.entry
    [:span.label "Sheet Date"]
    (fmt-date (:created_on info))]])

(defn render-printable-sheet [sheet-id]
  (let [info (data/count-sheet-info sheet-id)
        page-header (render-report-header info)]
    (core/render-printable
     (str "Count Sheet - " (fmt-date (:created_on info)))
     (report-page "Summary by Account" page-header
                  (sheet-summary-list sheet-id))
     (report-page "Contributors" page-header
                  (sheet-contributor-report sheet-id))
     (report-page "Checks" page-header
                  (sheet-check-deposit-sheet sheet-id)))))

(defn item-select-checkbox [item-id]
  [:input {:type "checkbox" :class "item-select" :name (str "item_" item-id)}])

(defn item-text-field [init-vals id error-field]
  (form/text-field {:class (class-set {"error" (= id error-field)})} (name id) (id init-vals)))

(defn item-edit-row [error-msg error-field init-vals post-target]
  (list
   (form/form-to {} [:post post-target]
                 [:tr
                  [:td.row-selector
                   (if-let [item-id (:item_id init-vals)]
                     (item-select-checkbox item-id))]
                  [:td (item-text-field init-vals :contributor error-field)]
                  [:td (item-text-field init-vals :check_number error-field)]
                  [:td (item-text-field init-vals :amount  error-field)]
                  [:td (account-selector {} "account_id" (:account_id init-vals))]
                  [:td
                   (item-text-field init-vals :notes error-field)
                   [:button.hidden-submit {:type "submit"} icon-check]]])
   (when error-msg
     [:tr {:class "error-message"} [:td {:colspan "85"} error-msg]])))

(defn item-display-row [sheet-id editable? dep-item]
  [:tr {:class "clickable-row" :data-href (str "/sheet/" sheet-id "?edit-item=" (:item_id dep-item))}
   (when editable?
     [:td.row-selector (item-select-checkbox (:item_id dep-item))])
   [:td.value (or (:contributor dep-item) [:span.informational "Plate"])]
   [:td.value (or (:check_number dep-item) [:span.informational "Cash"])]
   [:td.value (fmt-ccy (:amount dep-item))]
   [:td.value (:account_name dep-item)]
   [:td (:notes dep-item)]])

(defn render-sheet [sheet-id {error-msg :message error-field :field-name} init-vals edit-item]
  (let [info (data/count-sheet-info sheet-id)
        editable? (nil? (:final_on info))]
    (core/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)))
                       :include-js ["/petros-sheet.js"]
                       :sidebar (render-sheet-sidebar sheet-id :entry)
                       :toolbar (if editable?
                                  (list
                                   [:input.command {:id "delete_entries" :type "submit" :value "Delete Selected"}]
                                   [:input.command {:id "finalize_sheet" :type "submit" :value "Finalize Sheet"}])
                                  [:div#finalized-notice
                                   [:span.label
                                    "Sheet finalized on"]
                                   [:span.value
                                    (fmt-date (:final_on info))]
                                   [:span.label
                                    [:a {:href (sheet-printable-url sheet-id) :target "_blank"}
                                     "Open Sheet Report"]]])}
                      [:table.data.entries.full-width
                       (table-head (when editable? "")  "Contributor" "Check Number" "Amount" "Account" "Notes")
                       (map #(if (and editable?
                                      (parsable-integer? edit-item)
                                      (== (:item_id %) (parsable-integer? edit-item)))
                               (item-edit-row error-msg error-field % (str "/item/" (:item_id %)))
                               (item-display-row sheet-id editable? %))
                            (data/all-count-sheet-deposits sheet-id))
                       (when editable?
                         (list (if edit-item
                                 [:tr {:class "add-item-row" :data-href (str "/sheet/" sheet-id)}
                                  [:td {:colspan "6"}
                                   [:a {:href (sheet-url sheet-id)}
                                    "Add new item..."]]]
                                 (item-edit-row error-msg error-field init-vals (str "/sheet/" sheet-id)))))]
                      [:div.help
                       [:p
                        [:span.label "Contributor"] " - "
                        "Leave this blank for cash or other contributions without an attribution."]
                       [:p
                        [:span.label "Check Number"] " - "
                        "Leave this blank for cash contributions."]]
                      [:input#sheet-id {:type "hidden" :name "sheet-id" :value sheet-id}])))

(defn validate-integer [field-value fail-msg]
  (or (integer? field-value)
      (parsable-integer? field-value)
      (fail-validation fail-msg)))

(defn validate-field-integer [params field-name fail-msg]
  (let [field-value (field-name params)]
    (or (integer? field-value)
        (parsable-integer? field-value)
        (fail-validation field-name fail-msg))))

(defn validate-field-amount [params field-name fail-msg]
  (let [field-value (field-name params)]
    (if-let [amt (parsable-double? field-value)]
      (if (>= amt 0.0)
        amt
        (fail-validation field-name fail-msg))
      (fail-validation field-name fail-msg))))

(defn validate-field-check-number [params field-name fail-msg]
  (let [field-value (field-name params)]
    (if (string-empty? field-value)
      nil
      (or (parsable-integer? field-value)
          (fail-validation field-name fail-msg)))))

(defn validate-field-notes [params field-name fail-msg]
  (field-name params))

(defroutes sheet-routes
  (context "/sheet/:sheet-id" [sheet-id]
    (wrap-authorize-fn #(current-user-has-access-to-sheet? sheet-id)
                       (routes
                        (GET "/" {{edit-item :edit-item last-account-id :last_account_id} :params}
                          (render-sheet sheet-id nil {:account_id last-account-id} edit-item))

                        (POST "/" {params :params}
                          (log/info "Adding deposit: " params)
                          (with-validation #(render-sheet sheet-id % params nil)
                            (data/add-deposit (validate-integer sheet-id "Invalid sheet-id")
                                              (:contributor params)
                                              (validate-field-integer params :account_id        "Invalid account")
                                              (validate-field-amount params :amount             "Invalid amount")
                                              (validate-field-check-number params :check_number "Invalid check number")
                                              (validate-field-notes params :notes               "Invalid notes"))
                            (ring/redirect (str (sheet-url sheet-id) "?last_account_id=" (:account_id params)))))

                        (POST "/delete-items" {item-ids :body}
                          (data/delete-deposits-from-sheet sheet-id item-ids)
                          "Success")

                        (POST "/finalize" []
                          (data/finalize-sheet sheet-id (core/current-user-id))
                          "Success")

                        (GET "/summary" []
                          (render-sheet-summary sheet-id nil {}))

                        (GET "/printable" []
                          (render-printable-sheet sheet-id))

                        (GET "/checks" []
                          (render-sheet-checks sheet-id nil {}))))))

(defroutes item-routes
  (POST "/item/:item-id" {params :params}
    (let [{item-id :item-id} params
          sheet-id (data/deposit-count-sheet-id item-id)]
      (log/info "Updating deposit: " params)
      (with-validation #(render-sheet sheet-id % params item-id)
        (data/update-deposit (validate-integer item-id "Invalid item-id")
                             (:contributor params)
                             (validate-field-integer params :account_id        "Invalid account")
                             (validate-field-amount params :amount             "Invalid amount")
                             (validate-field-check-number params :check_number "Invalid check number")
                             (validate-field-notes params :notes               "Invalid notes"))
        (ring/redirect (sheet-url sheet-id))))))

(defn add-sheet-for-current-user []
  (let [sheet-id (data/add-count-sheet (core/current-user-id))]
    (ring/redirect (sheet-url sheet-id))))

(defroutes app-routes
  (GET "/" []  (render-sheet-list))
  (POST "/" [] (add-sheet-for-current-user))
  sheet-routes
  item-routes)


