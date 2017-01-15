(ns bookmarx.auth
  (:require [clojure.java.io :as io]
            [buddy.hashers :as hs]
            [buddy.sign.jws :as jws]
            [buddy.core.keys :as ks]
            [config.core :refer [env]]
            [clj-time.core :as t]
            [config.core :refer [env]]
            [bookmarx.ds :as ds])
  (:import java.sql.Timestamp))

(defn user-auth? "Validate a user name and password."
  [user password]
  (when (= user (env :user)) (hs/check password (ds/get-password))))

(defn get-pkey "Get the private key for authentication."
  []
  (ks/private-key (io/resource (env :private-key)) (env :pass-phrase)))

(defn create-auth-token "Create an authorization token given credentials."
  [user password]
  (let [exp (Timestamp. (.getMillis (t/plus (t/now) (t/days 1))))
        pkey (ks/private-key (io/resource (env :private-key)) (env :pass-phrase))]
    (when (user-auth? user password)
      (jws/sign (pr-str {:user user :claims ""}) pkey {:alg :rs256 :exp exp}))))

(defn get-auth-token [{:keys [:user :password] :as req}]
  (if-let [response (create-auth-token user password)]
      {:status 201 :body response}
      {:status 401 :body "Invalid credentials."}))
