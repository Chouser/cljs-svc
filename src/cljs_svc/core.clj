(ns cljs-svc.core
  (:refer-clojure :exclude [compile])
  (:import java.io.StringReader)
  (:use [ring.adapter.jetty :as raj]
        [ring.middleware.keyword-params :only (wrap-keyword-params)]
        [ring.middleware.params :only (wrap-params)]
        [clojure.repl :as repl :only ()]
        [cljs.compiler :as comp :only ()]))

(defn compile [cljs]
  (let [reader (java.io.PushbackReader. (StringReader. cljs))]
    (->> (repeatedly
           #(let [f (read reader false reader false)]
              (when-not (identical? f reader)
                (comp/emits
                  (comp/analyze {:ns (@comp/namespaces 'cljs.user)
                                 :context :expr
                                 :loclas {}} f)))))
         (take-while identity)
         (apply str))))

(defn -tojs [this cljs]
  (try
    ["js" (compile cljs)]
    (catch Throwable e
      (if (= (.getMessage e) "EOF while reading")
        ["incomplete"]
        ["err" (with-out-str (clojure.repl/pst))]))))

(defn cljs-svc [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str
           (try
             ["js" (compile (:form (:params req)))]
             (catch Throwable e
               (if (= (.getMessage e) "EOF while reading")
                 ["incomplete"]
                 ["err" (with-out-str
                          (binding [*err* *out*] (clojure.repl/pst e)))]))))})

(def app (wrap-params (wrap-keyword-params cljs-svc)))

(comp/load-file-vars "cljs_/core.cljs")

(defn -main []
  (let [port (Integer/parseInt (System/getenv "PORT"))]
    (raj/run-jetty app {:port port})))
