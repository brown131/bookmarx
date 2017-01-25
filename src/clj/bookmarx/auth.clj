(ns bookmarx.auth
  (:require [clojure.java.io :as io]
            [buddy.hashers :as hs]
            [buddy.sign.jws :as jws]
            [buddy.core.keys :as ks]
            [config.core :refer [env]]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [bookmarx.ds :as ds]))

(def redirect-whitelist
  [#"https://www.browncross.com/bookmarx/.*"
   #"http://localhost:3000/.*"
   #"http://localhost:3449/.*"])

(defn sign-token [token]
  (let [pkey (ks/private-key (io/resource (env :private-key)) (env :pass-phrase))]
    (jws/sign token pkey {:alg :rs256})))

(defn unsign-token [token]
  (jws/unsign token (ks/public-key (io/resource (env :public-key))) {:alg :rs256}))

(defn credentials-valid? "Validate a user name and password."
  [user password]
  (when (= user (env :user)) (hs/check password (ds/get-password))))

(defn create-auth-token "Create an authorization token given credentials."
  [{:keys [:user :password]}]
  (if (credentials-valid? user password)
    (let [exp (-> (env :auth-token-hours) t/hours t/from-now tc/to-long)
          auth-token (sign-token (pr-str {:user user :exp exp}))]
      {:status 201 :body (pr-str {:auth-token auth-token})})
    {:status 401 :body "Invalid credentials."}))

(defn wrap-auth-token [handler]
  (fn [request]
    (let [{:keys [:user :exp]} (when-let [auth-token (get-in request [:cookies "auth-token" :value])]
                                 (read-string (String. (unsign-token auth-token))))]
      (if (and user exp (< (tc/to-long (t/now)) exp))
        (handler (assoc request :auth-user user))
        (handler request)))))

(defn wrap-authentication [handler]
  (fn [request]
    (if (:auth-user request)
      (handler request)
      {:status 302
       :headers {"Location " (str (:prefix (env :client-env)) "/login?m=" (:uri request))}})))

(defn wrap-authorized-redirects [handler]
  (fn [request]
    (let [response (handler request)
          location (get-in response [:headers "Location"])]
      (if (and location (not-any? #(re-matches % location) redirect-whitelist))
        (assoc-in response [:headers "Location"] (str (:prefix (env :client-env)) "/"))
        response))))
