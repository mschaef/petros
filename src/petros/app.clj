(ns petros.app
  (:use petros.util
        compojure.core)
  (:require [clojure.tools.logging :as log]            
            [petros.data :as data]
            [petros.core :as core]
            [petros.user :as user]
            [petros.view :as view]
            [compojure.route :as route]
            [hiccup.form :as form]
            [compojure.handler :as handler]
            [ring.util.response :as ring]))

(def icon-pencil [:i {:class "fa fa-pencil fa-lg icon-pencil"}])
(def icon-check  [:i {:class "fa fa-check fa-lg icon-check"}])

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

(def fmt-ccy (formatter #(format "$%.2f" (ensure-bigdec %)) 0))
(def fmt-date (formatter #(format "%1$tB %1$te, %1$tY" %) ""))

(defn elem-has-attrs? [ elem ]
  (let [ obj (second elem) ]
    (if (map? obj)
      obj
      false)))

(defn elem-attrs [ elem ]
  (or (elem-has-attrs? elem)
      {}))

(defn elem-attr [ elem attr-name ]
  (get (elem-attrs elem) attr-name))

(defn elem-assoc-attrs [ elem new-attrs ]
  (if (elem-has-attrs? elem)
    (assoc elem 1 new-attrs)
    `[~(first elem) ~new-attrs ~@(rest elem)]))

(defn elem-merge-attrs [ elem new-attrs ]
  (elem-assoc-attrs elem (merge (elem-attrs elem) new-attrs)))

(defn map-body
  ([ f elem ]
     (if-let [ attrs (elem-has-attrs? elem) ]
       `[~(first elem) ~attrs ~@(map f (rest (rest elem)))]
       `[~(first elem) ~@(map f (rest elem))]))

  ([ f elem c1 ]
     (if-let [ attrs (elem-has-attrs? elem) ]
       `[~(first elem) ~attrs ~@(map f (rest (rest elem)) c1)]
       `[~(first elem) ~@(map f (rest elem) c1)])))

(defn zebra [ elem ]
  (map-body (fn [ selem class ]
              (elem-assoc-attrs selem { :class class }))
            elem
            (cycle ["even" "odd"])))

(defn table-head [ & tds ]
    (let [ [ attrs tds ]
         (if (map? tds)
           [ (first tds) (rest tds) ]
           [ {} tds ])]
      `[:thead
        [:tr ~attrs ~@(map (fn [ td ] [:th td]) tds)]]))

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

(defn render-home-page []
  (view/render-page {:page-title "Home" }

                    [:div.main-menu
                     [:div.entry
                      (form/form-to [:post "/"]
                                    [:input {:type "submit"
                                             :value "Create a New Count Sheet"}])]
                     [:div.entry
                      [:a {:href "/sheets"}
                       "See Existing Count Sheets"]]]))

(defn render-sheet-list []
  (view/render-page {:page-title "Count Sheets" }
                     [:table
                      (table-head "Creator" "Created On" "Total Amount" "" "")
                      (map #(let [ id (:count_sheet_id %) ]
                              (table-row (:email_addr %)
                                         (fmt-date (:created_on %))
                                         (fmt-ccy (:total_amount %))
                                         [:a { :href (sheet-url id) } "Entry"]
                                         [:a { :href (sheet-summary-url id) } "Sheet Summary"]))
                           (data/all-count-sheets))]))

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

(defn render-sheet-sidebar [ id ]
  (let [info (data/count-sheet-info id)]
    [:div.content
     [:div.total
      (fmt-ccy (:total_amount info))]
     [:div.entry
      (fmt-date (:created_on info))]
     [:div.entry
      (:email_addr info)]
     [:div.vspace]     
     [:div.entry
      [:a { :href (sheet-url id)} "Edit Contributions"]]
     [:div.entry
      [:a { :href (sheet-summary-url id)} "Sheet Summary"]]
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


(defn render-sheet-summary [id error-msg init-vals ]
  (let [info (data/count-sheet-info id)
        summary (data/count-sheet-summary id)
        summary-data (group-summary summary)]
    (view/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)) )
                       :sidebar (render-sheet-sidebar id)}
                      
                      [:h1 "Summary"]
                      [:table
                       (table-head "Account" "Check" "Cash" "Subtotal")
                       (map (fn [ acct-name ]
                              (table-row acct-name
                                         (fmt-ccy (get-in summary-data [ acct-name :check ]) "&nbsp;")
                                         (fmt-ccy (get-in summary-data [ acct-name :cash ]) "&nbsp;")
                                         (fmt-ccy (+ (get-in summary-data [ acct-name :check ] 0.0)
                                                     (get-in summary-data [ acct-name :cash ] 0.0)))))
                            (data/all-account-names))
                       (table-row "Total"
                                  (fmt-ccy (total-amounts (filter #(= :check (:type %)) summary)))
                                  (fmt-ccy (total-amounts (filter #(= :cash (:type %)) summary)))
                                  (fmt-ccy (total-amounts summary)))]
                    
                      [:h1 "Checks"]
                      [:table.checks
                       [:thead
                        [:tr
                         [:th "Contributor"]
                         [:th "Account"]
                         [:th "Amount"]
                         [:th "Check Number"]
                         [:th.full-width "Notes"]]]

                       (let [ checks (filter :check_number
                                             (data/all-count-sheet-deposits id))]
                         (if (> (count checks) 0)
                           (map #(table-row (:contributor %)
                                            (:account_name %)
                                            (fmt-ccy (:amount %))
                                            (or (:check_number %) "Cash")
                                            (:notes %))
                                checks)
                           [:tr [:td.no-checks { :colspan "5" } "No Checks"]]))])))

(defn item-edit-row [ sheet-id error-msg init-vals post-target cancel-target]
  (list
   (form/form-to { } [:post post-target]
                 (table-row (form/text-field { } "contributor" (:contributor init-vals))
                            (account-selector { } "account_id" (:account_id init-vals))
                            (form/text-field { } "amount" (:amount init-vals))
                            (form/text-field { } "check_number" (:check_number init-vals))
                            (form/text-field { :style "width:100%"} "notes" (:notes init-vals))
                            [:button { :type "submit" } icon-check ]))
   (when error-msg
     [:tr [:td {:class "error-message" :colspan "8"} error-msg]])))

(defn item-display-row [ sheet-id dep-item ]
  (log/error dep-item)
  (table-row { :class "clickable-row" :data-href (str "/sheet/" sheet-id "?edit-item=" (:item_id dep-item))}
             (or (:contributor dep-item) [:span.informational "Unattributed"])
             (:account_name dep-item)
             (fmt-ccy (:amount dep-item))
             (or (:check_number dep-item) [:span.informational "Cash"])
             (:notes dep-item)
             ""))

(defn render-sheet [ sheet-id error-msg init-vals edit-item ]
  (let [ info (data/count-sheet-info sheet-id) ]
    (view/render-page {:page-title (str "Count Sheet - " (fmt-date (:created_on info)))
                       :include-js [ "/petros-sheet.js" ]
                       :sidebar (render-sheet-sidebar sheet-id)}
                      [:table.form.entries
                       (table-head "Contributor" "Account" "Amount" "Check Number" "Notes" "")
                       (map #(if (and (parsable-integer? edit-item)
                                      (== (:item_id %) (parsable-integer? edit-item)))
                               (item-edit-row sheet-id error-msg % (str "/item/" (:item_id %))  (str "/sheet/" sheet-id))
                               (item-display-row sheet-id %))
                            (data/all-count-sheet-deposits sheet-id))                       
                       (if edit-item
                         [:tr { :class "clickable-row edit-row" :data-href (str "/sheet/" sheet-id )}
                          [:td {:colspan "6"}
                           [:a {:href (sheet-url sheet-id)}
                            "Add new item..."]]]
                         (item-edit-row sheet-id error-msg init-vals (str "/sheet/" sheet-id) (str "/sheet/" sheet-id)))]
                      [:div.help
                       [:p
                        [:span.label "Contributor"] " - "
                        "Leave this blank for cash or other contributions without an attribution."]
                       [:p
                        [:span.label "Check Number"] " - "
                        "Leave this blank for cash contributions."]])))


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
    (render-home-page))

  (GET "/sheets" []
    (render-sheet-list))
  
  (POST "/" []
    (let [ user-id (core/current-user-id) ]
      (log/info "Adding count-sheet: " user-id)
      (let [ sheet-id (data/add-count-sheet (core/current-user-id))]
        (ring/redirect (sheet-url sheet-id)))))

  (GET "/sheet/:sheet-id" { { sheet-id :sheet-id edit-item :edit-item last-account-id :last_account_id } :params }
    (log/info "Displaying sheet: " sheet-id)
    (render-sheet sheet-id nil { :account_id last-account-id} edit-item))

  (GET "/sheet/:sheet-id/summary" [ sheet-id ]
    (log/info "Displaying sheet summary: " sheet-id)
    (render-sheet-summary sheet-id nil {}))

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
                             (accept-integer account_id       "Invalid account")
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


