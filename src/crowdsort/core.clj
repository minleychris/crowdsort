(ns crowdsort.core
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.memcache :as memcache])
  (:use [hiccup.core]
        [compojure.core]))

;; Get from queue
(defn get-new-list []
  ;; FIXME: turn this into proper logging
  (println "Getting a new list")
  ;; FIXME: why does this give us a different list everytime? Could this indicate deeper problems?
  ;;(memcache/put! "current-list" (seq (map #(* 1000 (inc %)) (take 10 (map rand-int (repeat 10)))))))
  (memcache/put! "current-list" [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20]))

;; Database + memcache
(defn get-list []
  (if (not (memcache/contains? "current-list"))
    (get-new-list))
  (memcache/get "current-list"))

;; Get from memcache (probably)
(defn get-identifier []
  (let [id (rand-int 1000000)]
    (println (str "get-identifier " id))
    id))

(defn get-lock-key [idx]
  (str "index:" idx))

(defn get-lock-id [idx]
  (memcache/get (get-lock-key idx)))

(defn lock-index [idx id]
  (memcache/put! (get-lock-key idx) id :expiration (. com.google.appengine.api.memcache.Expiration byDeltaSeconds 30)))

(defn unlock-index [idx]
  (memcache/delete! (get-lock-key idx)))

(defn unlock-indexes [i j]
  (unlock-index i)
  (unlock-index j))

(defn list-sorted? []
  (>= (memcache/get "keeps")
     (count (get-list))))

(defn reset-keep-counter []
  (memcache/put! "keeps" 0))

(defn inc-keep-counter []
  (memcache/increment! "keeps" 1))

(defn invalidate-all-locks []
  (map #(memcache/delete! (get-lock-key %)) (range 0 (count (get-list)))))

(defn swap-values [i j]
  (reset-keep-counter)
  (let [list (get-list)]
    (memcache/put! "current-list" (-> list (assoc i (list j)) (assoc j (list i))))))

;; Email
(defn notify-list-owner [])

;; Misc

(defn is-not-locked? [idx]
  (println (str "is-not-locked? " idx ": " (nil? (get-lock-id idx)) " by " (get-lock-id idx)))
  (nil? (get-lock-id idx)))

(defn get-all-indexes []
  (shuffle (range 0 (count (get-list)))))

(defn get-and-lock-index [id]
  (let [idx (first (filter is-not-locked? (get-all-indexes)))]
    (println (str "get-and-lock-index idx: " idx))
    ;; if idx is nil?
    (lock-index idx id)
    idx))

(defn get-two-available-indexes [id]
  [(get-and-lock-index id) (get-and-lock-index id)])

(defn is-lock-held [i j id]
  (= (get-lock-id i) (get-lock-id j) id))

(defn keep-values [i j]
  (inc-keep-counter)
  )

(defn swap? [action]
  (= "swap" action))

(defn process-sorted-list []
  (if (list-sorted?)
    ((invalidate-all-locks)
     (reset-keep-counter)
     (notify-list-owner)
     (get-new-list))))

(defn process-action [i j id action]
  (if (is-lock-held i j id)
    (if (swap? action)
      (swap-values i j)
      ((keep-values i j)
       (process-sorted-list)))
    (unlock-indexes i j)))

(defn get-value [idx]
  (nth (get-list) idx))

(defn get-items-to-swap [id]
  (let [[idx1 idx2] (get-two-available-indexes id)]
    [{:index idx1 :value (get-value idx1)}
     {:index idx2 :value (get-value idx2)}]))

(defn swap-or-not-page []
  (let [id (get-identifier)
        [{i1 :index v1 :value} {i2 :index v2 :value}] (get-items-to-swap id)]
    (println (str "swap-or-not-page " id " " v1 " " v2))
    (html
     [:div (str v1 " " v2)]
     [:form {:method "POST"}
      [:input {:name "identifier" :value id :type "hidden"}]
      [:input {:name "index1" :value i1 :type "hidden"}]
      [:input {:name "index2" :value i2 :type "hidden"}]
      [:input {:name "action" :value "swap" :type "submit"}]
      [:input {:name "action" :value "keep" :type "submit"}]]
     [:div (str "Current list:" (seq (get-list)))])))

(defn handle-post [params]
  ;;do things
  (println params)
  (swap-or-not-page))

(defroutes crowdsort-app-handler
  (GET "/" req
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (swap-or-not-page)})
  (POST "/" {params :params}
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (handle-post params)})
  (ANY "*" _
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body "not found"}))

(ae/def-appengine-app crowdsort-app #'crowdsort-app-handler)
