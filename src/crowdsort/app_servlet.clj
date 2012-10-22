(ns crowdsort.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use crowdsort.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method crowdsort-app) this request response))
