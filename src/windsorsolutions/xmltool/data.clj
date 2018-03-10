(ns windsorsolutions.xmltool.data)

(defprotocol tree-node-protocol
  "Protocol all of our tree nodes must implement."
  (getName [node])
  (getValue [node]))

;; provides a record representing a tree node
(defrecord TreeNode [keyname value]
  tree-node-protocol
  (getName [this] keyname)
  (getValue [this] value))
