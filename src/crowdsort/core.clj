(ns crowdsort.core
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.memcache :as memcache]
            [appengine-magic.services.user :as user]
            [appengine-magic.services.datastore :as ds]
            [appengine-magic.services.mail :as mail])
  (:use [clojure.string :only [join split]]
        [hiccup core page element]
        [compojure.core]
        [compojure.route :as route]
        [ring.util.response :only [redirect]]
        [ring.middleware.params :only [wrap-params]]))

(ds/defentity ListToSort [elements owner-email submission-time])

;; Get from the datastore and set to memcache
(defn get-new-list []
  (let [new-list (or (first (ds/query :kind ListToSort :sort [:submission-time]))
                     ;; In the absence of any lists on the datastore, let's get a random one
                     (ListToSort. (vec (seq (take 10 (map rand-int (repeat 10)))))
                                  nil (.getTime (java.util.Date.))))]
    (memcache/put! "current-list" (:elements new-list))
    (memcache/put! "current-list-owner-email" (:owner-email new-list))))

;; Get from memcache
(defn get-list []
  (if (not (memcache/contains? "current-list"))
    (get-new-list))
  (memcache/get "current-list"))

(defn get-list-owner-email []
  (memcache/get "current-list-owner-email"))

(defn get-identifier []
  (let [id (rand-int 1000000)]
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

(defn number-of-keeps []
  (memcache/put! "keeps" 0 :policy :add-if-not-present)
  (memcache/get "keeps"))

(defn list-sorted? []
  (>= (number-of-keeps)
      (count (get-list))))

(defn reset-keep-counter []
  (memcache/put! "keeps" 0))

(defn inc-keep-counter []
  (memcache/increment! "keeps" 1))

(defn invalidate-all-locks []
  (map #(memcache/delete! (get-lock-key %)) (range 0 (count (get-list)))))

(defn swap-values [i j]
  (reset-keep-counter)
  (let [new-list (get-list)]
    (memcache/put! "current-list" (-> new-list (assoc i (new-list j)) (assoc j (new-list i))))))

;; Email
(declare format-list-to-display)

(defn notify-list-owner []
  (if (not (nil? (get-list-owner-email)))
    (println "Sending email to " (get-list-owner-email))
    (let [msg (mail/make-message :from "crowdsort@isnomore.net"
                                 :to (get-list-owner-email)
                                 :subject "Your list has been crowdsorted!"
                                 :text-body (str "We are glad to inform that your list has been crowsorted. Here it is:\n" (format-list-to-display (get-list))))])))

;; Misc

(defn is-not-locked? [idx]
  (nil? (get-lock-id idx)))

(defn get-all-indexes []
  (shuffle (range 0 (count (get-list)))))

(defn get-and-lock-index [id]
  (let [idx (first (filter is-not-locked? (get-all-indexes)))]
    ;; FIXME: what if idx is nil?
    (lock-index idx id)
    idx))

(defn get-two-available-indexes [id]
  (sort [(get-and-lock-index id) (get-and-lock-index id)]))

(defn is-lock-held [i j id]
  (= (get-lock-id i) (get-lock-id j) id))

(defn keep-values [i j]
  (inc-keep-counter)
  )

(defn swap? [action]
  (= "swap" action))

(defn process-sorted-list []
  (if (list-sorted?)
    (do (invalidate-all-locks)
     (reset-keep-counter)
     (notify-list-owner)
     (get-new-list))))

(defn process-action [i j id action]
  (if (is-lock-held i j id)
    (do (if (swap? action)
          (swap-values i j)
          (do (keep-values i j)
              (process-sorted-list)))
        (unlock-indexes i j))))

(defn process-submitted-array [array]
  ;; FIXME: notify user if things don't go well
  (if (user/user-logged-in?)
    (let [new-list (split array #"\s+")]
      (if (not (empty? new-list))
        (let [list-to-sort (ListToSort. new-list (.getEmail (user/current-user)) (.getTime (java.util.Date.)))]
          (ds/save! list-to-sort))))))

(defn get-value [idx]
  (nth (get-list) idx))

(defn get-items-to-swap [id]
  (let [[idx1 idx2] (get-two-available-indexes id)]
    [{:index idx1 :value (get-value idx1)}
     {:index idx2 :value (get-value idx2)}]))

(defn format-list-to-display [a-list]
  ;; FIXME: Add a parameter to return something line [0 1 2 ... 998 999 1000]
  (str "[" (join " " a-list) "]"))

(defn submit-new-list-page []
  (html5
   [:head
    [:title "Crowdsort - submit new array"]
    [:meta {:name "viewport" :content "width=768px, initial-scale=1.0"}]
    (include-css "/bootstrap/css/bootstrap.min.css"
                 "/bootstrap/css/bootstrap-responsive.min.css"
                 "/crowdsort.css")]
   [:body
    [:div.row-fluid
     [:div.span2.hidden-phone.hidden-tablet {:id "left-col"}]

     [:div.span7.container-fluid.visible-phone.visible-tablet.visible-desktop
      [:div.row-fluid
       [:div.page-header.span7
        [:h1 "Crowdsort"
         [:small " O(∞), Ω(1)"]]
        [:h3 "Submit an array!"]]]

      [:div.row-fluid
       [:div.span7
        ;; FIXME: auto-increasing number of input fields
        "Split items with one or more spaces:"
        [:form {:method "POST"}
         [:input {:name "array" :type "text"}]
         [:input.btn.btn-primary {:name "submit" :value "submit" :type "submit"}]]]]
      ]]]))

(defn swap-or-not-page []
  (let [id (get-identifier)
        [{i1 :index v1 :value} {i2 :index v2 :value}] (get-items-to-swap id)]
    (html5
     [:head
      [:title "Crowdsort"]
      [:meta {:name "viewport" :content "width=768px, initial-scale=1.0"}]
      (include-css "/bootstrap/css/bootstrap.min.css"
                   "/bootstrap/css/bootstrap-responsive.min.css"
                   "/crowdsort.css")]
     [:body
      [:div.row-fluid
       [:div.span2.hidden-phone.hidden-tablet {:id "left-col"}]

       [:div.span7.container-fluid.visible-phone.visible-tablet.visible-desktop
        [:div.row-fluid
         [:div.page-header.span7
          [:h1 "Crowdsort"
           [:small " O(∞), Ω(1)"]]]]

        [:div.row-fluid.sort-values-row
         [:div.span3.offset2
          [:span.sort-value.badge v1]
          [:span.value-spacer]
          [:span.sort-value.badge v2]]]

        [:div.row-fluid
         [:div.span3.offset2
          [:form {:method "POST"}
           [:input {:name "identifier" :value id :type "hidden"}]
           [:input {:name "index1" :value i1 :type "hidden"}]
           [:input {:name "index2" :value i2 :type "hidden"}]
           [:input.btn.btn-primary.btn-medium {:name "action" :value "keep" :type "submit"}]
           [:input.btn.btn-primary.btn-medium {:name "action" :value "swap" :type "submit"}]]]]

        [:div.list-row [:div.row-fluid
          [:div.span7.text-info "You are currently sorting:"]]
         [:div.row-fluid
          [:div.span7 [:code (format-list-to-display (get-list))]]]]]

      [:div.span3.hidden-phone.hidden-tablet {:id "right-col"}]]

      [:div.row-fluid
       [:div.span7.offset2.pull-left
        (if (user/user-logged-in?)
          (do
            [:div
             [:div.clearfix (link-to "/submit" "Submit an array for sorting")]
             [:div.clearfix (link-to (user/logout-url) "Logout")]]
            )
          (link-to (user/login-url) "Login to submit an array!"))]]

      (include-js "http://code.jquery.com/jquery-latest.js" "/bootstrap/js/bootstrap.min.js")])))

(defn handle-post [{action "action" index1 "index1" index2 "index2"  id "identifier"}]
  (process-action (Integer/parseInt index1) (Integer/parseInt index2) (Integer/parseInt id) action)
  (swap-or-not-page)
  )

(defn handle-submit [{array "array"}]
  (process-submitted-array array)
  (redirect "/"))

(defroutes crowdsort-app-handler
  (GET "/" req
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (swap-or-not-page)})
  (POST "/" {params :params}
        (handle-post params))
  (GET "/submit" req
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (submit-new-list-page)})
  (POST "/submit" {params :params}
        (handle-submit params))

  (route/resources "/")
  (route/not-found
   {:status 404
    :headers {"Content-Type" "text/plain"}
    :body "not found"}))

(ae/def-appengine-app crowdsort-app (wrap-params crowdsort-app-handler))
