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
(def icon-x  [:i {:class "fa fa-times fa-lg icon-x"}])

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
  (view/render-page {:page-title "Petros Count Sheets"
                     :sidebar (form/form-to [:post "/"]
                                            [:input {:type "submit"
                                                     :value "Create Sheet"}])}
                     [:table
                      (table-head "Creator" "Created On" "Total Amount" "" "")
                      (map #(let [ id (:count_sheet_id %) ]
                              (table-row (:email_addr %)
                                         (fmt-date (:created_on %))
                                         (fmt-ccy (:total_amount %))
                                         [:a { :href (sheet-url id) } "Entry"]
                                         [:a { :href (sheet-summary-url id) } "Summary"]))
                           (data/all-count-sheets))]))


(defn category-selector [ attrs id val ]
  [:select (merge attrs { :name id })
   (map (fn [ info ]
          [:option (assoc-if { :value (:category_id info) }
                             (== (:category_id info) (parsable-integer? val))
                             :selected ())
           (:name info)])
        (data/all-categories))])

(defn render-sheet-sidebar [ id ]
  (let [info (data/count-sheet-info id)]
    [:div.content
     [:div.entry
      [:span.label "Created On:"] (fmt-date (:created_on info))]
     [:div.entry
      [:span.label "Creator:"] (:email_addr info)]
     [:div.entry
      [:a { :href (sheet-url id)} "Entry"]]
     [:div.entry
      [:a { :href (sheet-summary-url id)} "Summary"]]]))

(defn group-summary [ summary ]
  (reduce (fn [ out s-entry ]
            (assoc-in out [ (:category s-entry) (:type s-entry) ] (:total s-entry)))
          {}
          summary))

(defn total-amounts [ summary ]
  (reduce (fn [ total s-entry ]
            (+ total (:total s-entry)))
          0
          summary))


(defn render-sheet-summary [id error-msg init-vals ]
  (let [summary (data/count-sheet-summary id)
        summary-data (group-summary summary)]
    (view/render-page {:page-title "Count Sheet"
                       :sidebar (render-sheet-sidebar id)}
                      
                      [:h1 "Summary"]
                      [:table
                       (table-head "Category" "Check" "Cash" "Subtotal")
                       (map (fn [ cat-name ]
                              (table-row cat-name
                                         (fmt-ccy (get-in summary-data [ cat-name :check ]) "&nbsp;")
                                         (fmt-ccy (get-in summary-data [ cat-name :cash ]) "&nbsp;")
                                         (fmt-ccy (+ (get-in summary-data [ cat-name :check ] 0.0)
                                                     (get-in summary-data [ cat-name :cash ] 0.0)))))
                            (data/all-category-names))
                       (table-row "Total"
                                  (fmt-ccy (total-amounts (filter #(= :check (:type %)) summary)))
                                  (fmt-ccy (total-amounts (filter #(= :cash (:type %)) summary)))
                                  (fmt-ccy (total-amounts summary)))]
                    
                      [:h1 "Checks"]
                      [:table
                       (table-head "Contributor" "Category" "Amount" "Check Number" "Notes")
                       (let [ checks (filter :check_number
                                             (data/all-count-sheet-deposits id))]
                         (if (> (count checks) 0)
                           (map #(table-row (:contributor %)
                                            (:category_name %)
                                            (fmt-ccy (:amount %))
                                            (or (:check_number %) "Cash")
                                            (:notes %))
                                checks)
                           [:tr [:td { :colspan "5" } "No Checks"]]))])))

(defn item-edit-row [ sheet-id error-msg init-vals post-target cancel-target]
  (list
   (form/form-to { } [:post post-target]
                 (table-row (form/text-field { } "contributor" (:contributor init-vals))
                            (category-selector { } "category_id" (:category_id init-vals))
                            (form/text-field { } "amount" (:amount init-vals))
                            (form/text-field { } "check_number" (:check_number init-vals))
                            (form/text-field { :style "width:100%"} "notes" (:notes init-vals))
                            (list
                             [:button { :type "submit" } icon-check ]
                             [:a {:href cancel-target} icon-x])))
   (when error-msg
     [:tr [:td {:class "error-message" :colspan "8"} error-msg]])))

(defn item-display-row [ sheet-id dep-item ]
  (table-row { :class "clickable-row" :data-href (str "/sheet/" sheet-id "?edit-item=" (:item_id dep-item))}
   (:contributor dep-item)
   (:category_name dep-item)
   (fmt-ccy (:amount dep-item))
   (or (:check_number dep-item) "Cash")
   (:notes dep-item)
   ""))

(defn render-sheet [ sheet-id error-msg init-vals edit-item ]
  (view/render-page {:page-title "Count Sheet"
                     :include-js [ "/petros-sheet.js" ]
                     :sidebar (render-sheet-sidebar sheet-id)}
                    [:table.form.entries
                     (table-head "Contributor" "Category" "Amount" "Check Number" "Notes" "")
                     (map #(if (and (parsable-integer? edit-item)
                                    (== (:item_id %) (parsable-integer? edit-item)))
                             (item-edit-row sheet-id error-msg % (str "/item/" (:item_id %))  (str "/sheet/" sheet-id))
                             (item-display-row sheet-id %))
                          (data/all-count-sheet-deposits sheet-id))                       
                     (if edit-item
                       [:tr { :class "clickable-row edit-row" :data-href (str "/sheet/" sheet-id )}
                        [:td {:colspan "6"} "Add new item..."]]
                       (item-edit-row sheet-id error-msg init-vals (str "/sheet/" sheet-id) (str "/sheet/" sheet-id)))]))


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

  (POST "/" []
    (data/add-count-sheet (core/current-user-id))
    (ring/redirect "/"))

  (GET "/sheet/:sheet-id" { { sheet-id :sheet-id edit-item :edit-item last-category-id :last_category_id } :params }
    (render-sheet sheet-id nil { :category_id last-category-id} edit-item))

  (GET "/sheet/:sheet-id/summary" [ sheet-id ]
    (render-sheet-summary sheet-id nil {}))

  (POST "/item/:item-id" { params :params }
    (let [ {item-id :item-id 
            category_id :category_id
            contributor :contributor
            amount :amount
            check-number :check_number
            notes :notes} params
            sheet-id (data/deposit-count-sheet-id item-id)]
      (log/info "update line item:" params) 
      (with-validation #(render-sheet sheet-id % params item-id)
        (data/update-deposit (accept-integer item-id           "Invalid item-id")
                             contributor
                             (accept-integer category_id       "Invalid category")
                             (accept-amount amount             "Invalid amount")
                             (accept-check-number check-number "Invalid check number")
                             (accept-notes notes               "Invalid notes")) 
        (ring/redirect (sheet-url sheet-id)))))

  (POST "/sheet/:sheet-id" { params :params }
    (let [ {sheet-id :sheet-id 
            category_id :category_id
            contributor :contributor
            amount :amount
            check-number :check_number
            notes :notes} params ]
      (log/info "add line item:" params) 
      (with-validation #(render-sheet sheet-id % params nil)
        (data/add-deposit (accept-integer sheet-id          "Invalid sheet-id")
                          contributor
                          (accept-integer category_id       "Invalid category")
                          (accept-amount amount             "Invalid amount")
                          (accept-check-number check-number "Invalid check number")
                          (accept-notes notes               "Invalid notes")) 
        (ring/redirect (str (sheet-url sheet-id) "?last_category_id=" category_id))))))


