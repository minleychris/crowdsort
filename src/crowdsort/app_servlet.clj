(ns crowd-sort.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use crowd-sort.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method crowd-sort-app) this request response))
