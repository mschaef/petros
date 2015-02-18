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

(defn sheet-url [ sheet-id ]
  (str "/sheet/" sheet-id))

(defn render-home-page []
  (view/render-page {:page-title "Petros Count Sheets" :sidebar "&nbsp;"}
                    (form/form-to [:post "/"]
                                  [:input {:type "submit"
                                           :value "Create Sheet"}])
                    [:table
                     [:tr 
                      [:td "Creator"]
                      [:td "Created On"]
                      [:td "Total Amount"]]
                     (map (fn [ cs ]
                            [:tr
                             [:td (:email_addr cs)]
                             [:td [:a {:href (sheet-url (:count_sheet_id cs))} (:created_on cs)]]
                             [:td (str "$" (:total_amount cs))]])
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

(defn render-sheet-summary [id error-msg init-vals ]
  (view/render-page {:page-title "Count Sheet"
                     :sidebar (render-sheet-sidebar id)}

                    [:h1 "Summary"]
                    (let [summary (data/count-sheet-summary id)
                          summary-data (group-summary summary)]
                      [:table
                       [:tr
                        [:td "Category"]
                        [:td "Check"]
                        [:td "Cash"]
                        [:td "Subtotal"]]
                       (map (fn [ cat-name ]
                              [:tr
                               [:td cat-name]
                               [:td (fmt-ccy (get-in summary-data [ cat-name :check ]) "&nbsp;")]
                               [:td (fmt-ccy (get-in summary-data [ cat-name :cash ]) "&nbsp;")]
                               [:td (fmt-ccy (+ (get-in summary-data [ cat-name :check ] 0.0)
                                                (get-in summary-data [ cat-name :cash ] 0.0)))]])
                            (data/all-category-names))
                       [:tr
                        [:td "Total"]
                        [:td (fmt-ccy (total-amounts (filter #(= :check (:type %)) summary)))]
                        [:td (fmt-ccy (total-amounts (filter #(= :cash (:type %)) summary)))]
                        [:td (fmt-ccy (total-amounts summary))]]])
                    
                    [:h1 "Checks"]
                    [:table
                     [:tr
                      [:td "Contributor"]
                      [:td "Category"]
                      [:td "Amount"]
                      [:td "Check Number"]
                      [:td "Notes"]]
                     (map (fn [ dep ]
                            [:tr
                             [:td (:name dep)]
                             [:td (:category dep)]
                             [:td (fmt-ccy (:amount dep))]
                             [:td (or (:check_number dep) "Cash")]
                             [:td (:notes dep)]])
                          (filter :check_number
                                  (data/all-count-sheet-deposits id)))]))

(defn render-sheet [ id error-msg init-vals ]
  (view/render-page {:page-title "Count Sheet"
                     :sidebar (render-sheet-sidebar id)}
                    (form/form-to { } [:post (str "/sheet/" id)]
                                  [:table
                                   [:tr
                                    [:td]
                                    [:td "Contributor"]
                                    [:td "Category"]
                                    [:td "Amount"]
                                    [:td "Check Number"]
                                    [:td "Notes"]
                                    [:td]]
                                   [:tr
                                    [:td]                                    
                                    [:td (form/text-field { } "contributor" (:contributor init-vals))]
                                    [:td (category-selector { } "category-id" (:category-id init-vals))]
                                    [:td (form/text-field { } "amount" (:amount init-vals))]
                                    [:td (form/text-field { } "check-number" (:check-number init-vals))]
                                    [:td (form/text-field { } "notes" (:notes init-vals))]
                                    [:td (form/submit-button { } "Add Item")]]
                                   [:tr [:td {:colspan "6"} error-msg]]
                                   (map (fn [ dep ]
                                          [:tr
                                           [:td [:a {:href (str "/sheet/" id "?edit-item=" (:item_id dep))}
                                                 [:i {:class "fa fa-pencil fa-lg"}]]]
                                           [:td (:name dep)]
                                           [:td (:category dep)]
                                           [:td (fmt-ccy (:amount dep))]
                                           [:td (or (:check_number dep) "Cash")]
                                           [:td (:notes dep)]
                                           [:td]])
                                        (data/all-count-sheet-deposits id))])))


(defn accept-integer [ obj message ]
  (or (parsable-integer? obj)
      (fail-validation message)))

(defn accept-double [ obj message ]
  (or (parsable-double? obj)
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

  (GET "/sheet/:sheet-id" [ sheet-id ]
    (render-sheet sheet-id nil {}))

  (GET "/sheet/:sheet-id/summary" [ sheet-id ]
    (render-sheet-summary sheet-id nil {}))

  (POST "/sheet/:sheet-id" { params :params }
    (let [ {sheet-id :sheet-id 
            category-id :category-id
            contributor :contributor
            amount :amount
            check-number :check-number
            notes :notes} params ]
      (log/info "add line item:" params) 
      (with-validation #(render-sheet sheet-id % params)
        (data/add-deposit (accept-integer sheet-id          "Invalid sheet-id")
                          contributor
                          (accept-integer category-id       "Invalid category")
                          (accept-double amount             "Invalid sheet-id")
                          (accept-check-number check-number "Invalid check number")
                          (accept-notes notes               "Invalid notes")) 
        (ring/redirect (sheet-url sheet-id))))))


