(ns core
  (:require [clojure.string :as string]
            [hiccup.core :as hiccup])
  (:import (java.util Properties)
           (javax.activation DataHandler DataSource FileDataSource)
           (javax.mail Message
                       Message$RecipientType
                       MessagingException
                       Session)
           (javax.mail.internet AddressException
                                InternetAddress
                                MimeBodyPart
                                MimeMessage
                                MimeMultipart)))

(defn mime-multipart-dispatch [data]
  (let [tag-type (first data)]
   (cond
     (= "text" (namespace tag-type))
     :body-part
     :else
     tag-type)))

(defmulti ->raw-message-body #'mime-multipart-dispatch)


(defn- set-message-recipients [^MimeMessage message recipients]
  (doseq [[t address] recipients]
    (cond
      (= :to t)
      (.setRecipients message Message$RecipientType/TO (InternetAddress/parse address))
      (= :cc t)
      (.setRecipients message Message$RecipientType/CC (InternetAddress/parse address))
      (= :bcc t)
      (.setRecipients message Message$RecipientType/BCC (InternetAddress/parse address)))))

(defmethod ->raw-message-body :message [[_ & children]]
  (let [message (MimeMessage. (Session/getDefaultInstance (Properties.)))]
    (when (map? (first children))
      (let [{:keys [subject from recipients]} (first children)]
        (when subject
          (.setSubject message subject "UTF-8"))
        (when from
          (.setFrom message (InternetAddress. from)))
        (when recipients
          (set-message-recipients message recipients))))
    (let [content (if (map? (first children)) (second children) (first children))]
      (.setContent message (->raw-message-body content)))
    message))

(defmethod ->raw-message-body :multipart/mixed [[_ & children]]
  (let [mime-multipart (MimeMultipart. "mixed")]
    ;; TODO: does this have properties
    (let [children (if (map? (first children)) (rest children) children)
          evaled-children (map ->raw-message-body children)]
      (doseq [child evaled-children]
        (.addBodyPart mime-multipart child)))
    mime-multipart))

(defmethod ->raw-message-body :multipart/alternative [[_ & children]]
  (let [mime-multipart (MimeMultipart. "alternative")]
    ;; TODO: does this have properties
    (let [children (if (map? (first children)) (rest children) children)
          evaled-children (map ->raw-message-body children)]
      (doseq [child evaled-children]
        (.addBodyPart mime-multipart child)))
    mime-multipart))

(def encodings
  {"utf-8" "UTF-8"
   "UTF-8" "UTF-8"})

(defn- fetch-content-data-dispatch [content-type content-subtype children]
  [content-type content-subtype])

(defmulti fetch-content-data #'fetch-content-data-dispatch)
(defmethod fetch-content-data ["text" "plain"] [_ _ children]
  (apply str children))
(defmethod fetch-content-data ["text" "html"] [_ _ children]
  (cond
    (and (= 1 (count children))
         (vector? (first children)))
    (hiccup/html children)
    :else
    (apply str children)))

(defn- make-data-handler [[type param]]
  (cond
    (= :file-data-source type)
    (DataHandler. (FileDataSource. param))))

(defmethod ->raw-message-body :body-part [[tag & children]]
  (cond
    (namespace tag)
    (let [content-type (namespace tag)
          name-parts (string/split (name tag) #"\.")
          content-subtype (first name-parts)
          encoding (second name-parts)
          content-type-str (format "%s/%s; charset=%s" content-type content-subtype (get encodings encoding))
          content-data (fetch-content-data content-type content-subtype children)
          body-part (MimeBodyPart.)]
      (.setContent body-part content-data content-type-str)
      body-part)
    :else
    (let [body-part (MimeBodyPart.)
          {:keys [filename data-handler] :as props} (and (map? (first children)) (first children))
          child (if props (second children) (first children))
          child' (and child (->raw-message-body child))]
      (when data-handler
        (.setDataHandler body-part (make-data-handler data-handler)))
      (when filename
        (.setFileName body-part filename))
      (when child'
        (.setContent body-part child'))
      body-part)))

(comment
  (Session/getDefaultInstance (Properties.))
  (.writeTo
   (->raw-message-body
    [:message {:subject "Customer service contact info"
               :from "Sender Name <sender@example.com>"
               :recipients [[:to "recipient@example.com"]
                            [:cc "recipient2@example.com"]]}
     [:multipart/mixed
      [:body-part
       [:multipart/alternative
        [:text/plain.utf-8
         "Hello, \r\n"
         "Please see the attached file for a list"
         "of customers to contact."]
        [:text/html.utf-8
         [:html
          [:head]
          [:body
           [:h1 "Hello!"]
           [:p "Please see the attached file for a list of customers"]]]]]]
      [:body-part {:filename "cutomers-to-contact-xlsx"
                   :data-handler [:file-data-source "src/core.clj"]}]]])
   System/out)
  )
