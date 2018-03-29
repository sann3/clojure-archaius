(ns archaius-test.core
  (:require [mount.core :refer [defstate] :as mount]
            [cheshire.core :refer [parse-string]])
  (:import (org.postgresql.ds PGSimpleDataSource)
           (com.netflix.config DynamicConfiguration
                               DynamicPropertyFactory
                               DynamicStringProperty
                               FixedDelayPollingScheduler
                               ConcurrentCompositeConfiguration
                               ConfigurationManager)
           (com.netflix.config.util ConfigurationUtils)
           (com.netflix.config.sources JDBCConfigurationSource)
           (javax.sql DataSource)))

(defstate archaius-config :start
  (let [ds (PGSimpleDataSource.)
        _ (.setUrl ds (System/getenv "DATABASE_URL"))
        source (JDBCConfigurationSource. ds "select distinct key, value from config" "key" "value")
        poll-interval-in-ms 60000
        scheduler (FixedDelayPollingScheduler. 0  poll-interval-in-ms false)]
    (DynamicConfiguration. source scheduler)))

(defstate archaius :start
  (let [final-config (ConcurrentCompositeConfiguration.)
        _ (.addConfiguration final-config archaius-config)]
    (ConfigurationManager/install final-config)))

(defn get-config
  ([key]
   (get-config key nil))
  ([key default-value]
   (-> (DynamicPropertyFactory/getInstance) (.getStringProperty key default-value) (.get))))

(defn all-config []
  (->> (ConfigurationUtils/getProperties archaius-config)
       (reduce (fn [m [k v]] (assoc m k (parse-string (get-config k)))) {})))

(defn -main
  "Get all properties which is loaded in Archaius via DB datasource"
  [& args]
  ((mount/start #'archaius-config)
   (mount/start #'archaius)
   (println "All properties :"  (all-config))))
