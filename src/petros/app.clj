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


(defn ensure-bigdec [ val ]
  (if (= (.getClass val) java.math.BigDecimal)
    val
    (java.math.BigDecimal. val)))

(defn fmt-ccy
  ([ amount ]
      (fmt-ccy amount 0))
  ([ amount default ]
     (if (number? amount)
       (format "$%.2f" (ensure-bigdec amount))
       (if (number? default)
         (fmt-ccy default)
         default))))

(defn table-head [ & tds ]
  `[:thead
    [:tr ~@(map (fn [ td ] [:th td]) tds)]])

(defn table-row [ & tds ]
  `[:tr ~@(map (fn [ td ] [:td td]) tds)])

(defn sheet-url [ sheet-id ]
  (str "/sheet/" sheet-id))

(defn render-home-page []
  (view/render-page {:page-title "Petros Count Sheets" :sidebar "&nbsp;"}
                    (form/form-to [:post "/"]
                                  [:input {:type "submit"
                                           :value "Create Sheet"}])
                    [:table
                     (table-head "Creator" "Created On" "Total Amount")
                     (map #(table-row (:email_addr %)
                                      [:a {:href (sheet-url (:count_sheet_id %))} (:created_on %)]
                                      (fmt-ccy (:total_amount %)))
                          (data/all-count-sheets))]))


(defn category-selector [ attrs id val ]
  [:select (merge attrs { :name id })
   (map (fn [ info ]
          [:option { :value (:category_id info)}
           (:name info)])
        (data/all-categories))])

(defn render-sheet-sidebar [ id ]
  [:ul
   [:li [:a { :href (str "/sheet/" id)} "Entry"]]
   [:li [:a { :href (str "/sheet/" id "/summary")} "Summary View"]]])

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
  (view/render-page {:page-title "Count Sheet"
                     :sidebar (render-sheet-sidebar id)}

                    [:h1 "Summary"]
                    (let [summary (data/count-sheet-summary id)
                          summary-data (group-summary summary)]
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
                                  (fmt-ccy (total-amounts summary)))])
                    
                    [:h1 "Checks"]
                    [:table
                     (table-head "Contributor" "Category" "Amount" "Check Number" "Notes")
                     (map #(table-row (:contributor %)
                                      (:category_name %)
                                      (fmt-ccy (:amount %))
                                      (or (:check_number %) "Cash")
                                      (:notes %))
                          (filter :check_number
                                  (data/all-count-sheet-deposits id)))]))

(defn item-edit-row [ sheet-id error-msg init-vals post-target]
  (list
   (form/form-to { } [:post post-target]
                 (table-row ""
                            (form/text-field { } "contributor" (:contributor init-vals))
                            (category-selector { } "category_id" (:category_id init-vals))
                            (form/text-field { } "amount" (:amount init-vals))
                            (form/text-field { } "check_number" (:check_number init-vals))
                            (form/text-field { } "notes" (:notes init-vals))
                            (form/submit-button { } "Add Item")))
   [:tr [:td {:colspan "6"} error-msg]]))

(defn item-display-row [ sheet-id dep-item ]
  (table-row [:a {:href (str "/sheet/" sheet-id "?edit-item=" (:item_id dep-item))}
              [:i {:class "fa fa-pencil fa-lg"}]]
             (:contributor dep-item)
             (:category_name dep-item)
             (fmt-ccy (:amount dep-item))
             (or (:check_number dep-item) "Cash")
             (:notes dep-item)))

(defn render-sheet [ sheet-id error-msg init-vals edit-item ]
  (view/render-page {:page-title "Count Sheet"
                     :sidebar (render-sheet-sidebar sheet-id)}
                    [:table
                     (table-head "" "Contributor" "Category" "Amount" "Check Number" "Notes")
                     (unless edit-item
                       (item-edit-row sheet-id error-msg init-vals (str "/sheet/" sheet-id)))
                     (map #(if (and (parsable-integer? edit-item)
                                    (== (:item_id %) (parsable-integer? edit-item)))
                             (item-edit-row sheet-id error-msg % (str "/item/" (:item_id %)))
                             (item-display-row sheet-id %))
                          (data/all-count-sheet-deposits sheet-id))]))


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

  (GET "/sheet/:sheet-id" { { sheet-id :sheet-id edit-item :edit-item } :params }
    (render-sheet sheet-id nil {} edit-item))

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
        (ring/redirect (sheet-url sheet-id))))))


