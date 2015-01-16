(ns petros.user
  (:use petros.util
        compojure.core)
  (:require [cemerick.friend.credentials :as credentials]
            [ring.util.response :as ring]
            [cemerick.friend :as friend]
            [hiccup.form :as form]
            [petros.core :as core]
            [petros.data :as data]
            [petros.view :as view]))

(defn render-login-page [ & { :keys [ email-addr login-failure?]}]
  (view/render-page { :page-title "Log In" }
   (form/form-to
    [:post "/login"]
    [:table { :class "form" }
     [:tr
      [:td "E-Mail Address:"]
      [:td (form/text-field { :class "simple-border" } "username" email-addr)]]
     [:tr
      [:td "Password:"]
      [:td (form/password-field { :class "simple-border" } "password")]]
     [:tr [:td { :colspan 4 }
           (if login-failure?
             [:div#error "Invalid username or password."])]]
     [:tr 
      [:td { :colspan 4 }
       [:center
        [:a { :href "/user"} "Create New User"]
        " - "
        (form/submit-button {} "Login")]]]])))

(defn render-new-user-form [ & { :keys [ error-message ]}]
  (view/render-page { :page-title "Create New User" }
   (form/form-to
    [:post "/user"]
    [:table { :class "form" }
     [:tr
      [:td "E-Mail Address:"]
      [:td  (form/text-field { :class "simple-border" } "email_addr")]]
     [:tr
      [:td "Password:"]
      [:td (form/password-field { :class "simple-border" } "password")]]
     [:tr
      [:td "Verify Password:"]
      [:td (form/password-field { :class "simple-border" } "password2")]]
     
     (unless (empty? error-message)
       [:tr [:td { :colspan 2 } [:div#error error-message]]])
     
     [:tr [:td ] [:td (form/submit-button {} "Create User")]]])))

(defn create-user  [ email-addr password ]
  (data/add-user email-addr password))

(defn add-user [ email-addr password password2 ] 
  (cond
   (data/user-email-exists? email-addr)
   (render-new-user-form :error-message "User with this e-mail address already exists.")

   (not (= password password2))
   (render-new-user-form :error-message "Passwords do not match.")

   :else
   (do
     (create-user email-addr (credentials/hash-bcrypt password))
     (ring/redirect "/"))))

(defroutes public-routes
  (GET "/user" []
       (render-new-user-form))

  (POST "/user" {{email-addr :email_addr password :password password2 :password2} :params}
        (add-user email-addr password password2))

  (friend/logout (ANY "/logout" []  (ring.util.response/redirect "/")))

  (GET "/login" { { login-failed :login_failed email-addr :username } :params }
       (render-login-page :email-addr email-addr
                          :login-failure? (= login-failed "Y"))))

