(ns tailrecursion.boot.task.ring
  (:require
   [tailrecursion.boot.core        :as core]
   [ring.adapter.jetty             :as jetty]
   [ring.middleware.session        :as session]
   [ring.middleware.session.cookie :as cookie]
   [ring.middleware.head           :as head]
   [ring.middleware.file           :as file]
   [ring.middleware.file-info      :as file-info]))

(def server     (atom nil))
(def middleware (atom identity))

(defn ring-task [mw]
  (swap! middleware comp mw)
  identity)

(defn handle-404
  [req]
  {:status 404 :headers {} :body "Not Found :("})

(core/deftask files
  [& [docroot]]
  (let [out (core/get-env :out-path)]
    (ring-task #(-> %
                  (file/wrap-file (or docroot out))
                  (file-info/wrap-file-info)))))

(core/deftask head
  []
  (ring-task head/wrap-head))

(core/deftask session-cookie
  [& [key]]
  (let [dfl-key "a 16-byte secret"
        store   (cookie/cookie-store {:key (or key dfl-key)})]
    (ring-task #(session/wrap-session % {:store store}))))

(core/deftask dev-mode
  []
  (let [set-dev #(assoc % "X-Dev-Mode" "true")
        add-hdr #(update-in % [:headers] set-dev)]
    (ring-task #(comp add-hdr %))))

(core/deftask jetty
  [& {:keys [port join?] :or {port 8000 join? false}}]
  (println
    "Jetty server stored in atom here: #'tailrecursion.boot.task.ring/server...")
  (core/with-pre-wrap
    (swap! server
      #(or % (-> (@middleware handle-404)
               (jetty/run-jetty {:port port :join? join?}))))))

(core/deftask dev-server
  []
  (comp (head) (dev-mode) (session-cookie) (files) (jetty)))
