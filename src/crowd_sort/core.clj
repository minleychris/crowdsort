(ns crowd-sort.core
  (:require [appengine-magic.core :as ae])
  (:use [hiccup.core]))

;; Get from queue
(defn get-new-list []
  (def current-list (map #(* 1000 (inc %)) (take 10 (map rand-int (repeat 10))))))

;; Database + memcache
(defn get-list []
  current-list)

;; Get from memcache (probably)
(defn get-identifier []
  (int (rand 1000000)))

(defn get-lock-id [idx]
  )

(defn lock-index [idx]
  )

(defn unlock-indexes [i j])

(defn list-sorted? []
  ;; number of keeps == (length list)
  )

(defn reset-keep-counter [])

(defn inc-keep-counter [])

(defn invalidate-all-locks [])

;; Email
(defn notify-list-owner [])

;; Misc

(defn is-not-locked? [idx]
  (nil? (get-lock-id idx)))

(defn get-all-indexes []
  (shuffle (range 0 (count (get-list)))))

(defn get-and-lock-index [id]
  (let [idx (first (filter is-not-locked? (get-all-indexes)))]
    ;; if idx is nil?
    (lock-index idx)
    idx))

(defn get-two-available-indexes [id]
  [(get-and-lock-index id) (get-and-lock-index id)])

(defn is-lock-held [i j id]
  (= (get-lock-id i) (get-lock-id j) id))

(defn keep-values [i j]
  (inc-keep-counter)
  )

(defn swap-values [i j]
  (reset-keep-counter)
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
    (html
     [:div (str v1 " " v2)]
     [:form
      [:input {:name "identifier" :value id :type "hidden"}]
      [:input {:name "index1" :value i1 :type "hidden"}]
      [:input {:name "index2" :value i2 :type "hidden"}]
      [:input {:name "action" :value "swap" :type "submit"}]
      [:input {:name "action" :value "keep" :type "submit"}]])))
           
(defn crowd-sort-app-handler [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (swap-or-not-page)})


(ae/def-appengine-app crowd-sort-app #'crowd-sort-app-handler)
