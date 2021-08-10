# Data MIME

This library is a proof-of-concept for wrapping the javax.mail library
into a declarative, data-oriented API.

## Example

The following is an example which does the same thing that the Java
example on [this page](https://docs.aws.amazon.com/ses/latest/DeveloperGuide/send-email-raw.html) tries to do:

```clojure
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
```
