(ns crowd-sort.core
  (:require [appengine-magic.core :as ae])
  (:use [hiccup.core]))

(defn get-list-to-sort []
  [1 2 3 4 5])

(defn get-identifier [])

(defn get-and-lock-index [id]
  (int (rand 10000)))

(defn get-two-available-indexes [id]
  [(get-and-lock-index id) (get-and-lock-index id)])

(defn is-lock-held [i j id])

(defn swap-values [i j])

(defn unlock-indexes [i j])

(defn swap? [action]
  (= "swap" action))

(defn process-action [i j id action]
  (if (is-lock-held i j id)
    (if (swap? action)
      (swap-values i j)
      )
    (unlock-indexes i j)))

(defn get-value [idx]
  4)

(defn get-items-to-swap [id]
  (let [indexes (get-two-available-indexes id)]
    (zipmap indexes (map get-value indexes))))

(defn swap-or-not-page []
  (let [id (get-identifier)
        [i1 v1 i2 v2] (get-items-to-swap id)]
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


