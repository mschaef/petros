(ns petros.user
  (:use petros.util
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [cemerick.friend.credentials :as credentials]
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
        [:a { :href "/user"} "Register New User"]
        " - "
        (form/submit-button {} "Login")]]]])))

(defn render-new-user-form [ & { :keys [ error-message ]}]
  (view/render-page { :page-title "Register New User" }
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
     
     [:tr [:td ] [:td (form/submit-button {} "Register User")]]])))

(defn add-user [ email-addr password password2 ] 
  (cond
   (data/user-email-exists? email-addr)
   (render-new-user-form :error-message "User with this e-mail address already exists.")

   (not (= password password2))
   (render-new-user-form :error-message "Passwords do not match.")

   :else
   (do
     (log/info "Creating user: " email-addr)
     (let [ user-id  (data/add-user email-addr (credentials/hash-bcrypt password)) ]
       (data/set-user-roles user-id #{:petros.role/verified}))
     (ring/redirect "/"))))

(defn render-change-password-form  [ & { :keys [ error-message ]}]
  (view/render-page { :page-title "Change Password" }
   (form/form-to
    [:post "/user/password"]
    [:table { :class "form" }
     [:tr
      [:td "E-Mail Address:"]
      [:td  (core/authenticated-username)]]
     [:tr
      [:td "Old Password:"]
      [:td (form/password-field { :class "simple-border" } "password")]]
     [:tr
      [:td "New Password:"]
      [:td (form/password-field { :class "simple-border" } "new_password1")]]
     [:tr
      [:td "Verify Password:"]
      [:td (form/password-field { :class "simple-border" } "new_password2")]]
     
     (unless (empty? error-message)
       [:tr [:td { :colspan 2 } [:div#error error-message]]])
     
     [:tr [:td ] [:td (form/submit-button {} "Change Password")]]]))  )

(defn change-password [ password new-password-1 new-password-2 ]
  (let [ email-addr (core/authenticated-username) ]
    (cond
     (not (core/password-matches? password))
     (render-change-password-form :error-message "Old Password Incorrect")

     (not (= new-password-1 new-password-2))
     (render-change-password-form :error-message "Passwords do not match.")

     :else
     (do
       (log/info "Changing Password for user:" email-addr)
       (data/set-user-password email-addr (credentials/hash-bcrypt new-password-1))
       (ring/redirect "/")))))

(defroutes public-routes
  (GET "/user" []
       (render-new-user-form))

  (POST "/user" {{email-addr :email_addr password :password password2 :password2} :params}
        (add-user email-addr password password2))


  (friend/logout (ANY "/logout" []  (ring.util.response/redirect "/")))
  
  (GET "/login" { { login-failed :login_failed email-addr :username } :params }
    (render-login-page :email-addr email-addr
                       :login-failure? (= login-failed "Y"))))

(defroutes private-routes
  (GET "/user/password" []
       (render-change-password-form))

  (POST "/user/password" {{password :password new-password-1 :new_password1 new-password-2 :new_password2} :params}
    (change-password password new-password-1 new-password-2)))
