(ns echowaves.routes.auth
  (:require [compojure.core :refer [defroutes GET POST]]
            [echowaves.views.layout :as layout]            
            [noir.session :as session]
            [noir.response :as resp]
            [noir.validation :as vali]
            [noir.util.crypt :as crypt]
            [echowaves.models.db :as db]
            [echowaves.util :refer [echowaves-path]]
            [echowaves.routes.upload :refer [delete-image]]
            [noir.util.route :refer [restricted]])
  (:import java.io.File))

(defn create-echowaves-path []  
  (let [user-path (File. (echowaves-path))]
    (if-not (.exists user-path) (.mkdir user-path))
    (str (.getAbsolutePath user-path) File/separator)))

(defn valid? [id pass pass1]
  (vali/rule (vali/has-value? id)
             [:id "wave name is required"])
  (vali/rule (vali/min-length? pass 5)
             [:pass "password must be at least 5 characters"]) 
  (vali/rule (= pass pass1)
             [:pass "entered passwords do not match"])
  (not (vali/errors? :id :pass :pass1)))

(defn registration-page [& [id]]
  (layout/render "registration.html"
                 {:id id
                  :id-error (first (vali/get-errors :id))
                  :pass-error (first (vali/get-errors :pass))}))

(defn format-error [id ex]
  (cond
    (and (instance? org.postgresql.util.PSQLException ex) 
      (= 0 (.getErrorCode ex))) 
    (str "The wave name " id " already exists!")
    
    :else
    "An error has occured while processing the request"))

(defn handle-registration [id pass pass1]
  (if (valid? id pass pass1)
    (try        
      (db/create-user {:id id :pass (crypt/encrypt pass)})      
      (session/put! :user id)
      (create-echowaves-path)
      (resp/redirect "/")
      (catch Exception ex
        (vali/rule false [:id (format-error id ex)])
        (registration-page)))
    (registration-page id)))

(defn handle-login [id pass]
  (let [user (db/get-user id)] 
    (if (and user (crypt/compare pass (:pass user)))
      (session/put! :user id)))

  (resp/redirect "/"))

(defn handle-logout []
  (session/clear!)
  (resp/redirect "/"))

(defn delete-account-page []  
  (layout/render "deleteAccount.html"))

(defn handle-confirm-delete []
  (let [user (session/get :user)] 
    (doseq [{:keys [name]} (db/images-by-user user)]      
      (delete-image user name))    
    (clojure.java.io/delete-file (echowaves-path))
    (db/delete-user user))
  (session/clear!)
  (resp/redirect "/"))

(defroutes auth-routes 
  (GET "/register" [] 
       (registration-page))
  
  (POST "/register" [id pass pass1] 
        (handle-registration id pass pass1))
  
  (POST "/login" [id pass] 
        (handle-login id pass))
  
  (GET "/logout" [] 
       (handle-logout))
  
  (GET "/delete-account" [] 
       (restricted (delete-account-page)))
  
  (POST "/confirm-delete" [] 
        (restricted (handle-confirm-delete))))
