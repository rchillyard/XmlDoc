package com.phasmidsoftware

import com.phasmidsoftware.core.Utilities.renderNode
import org.slf4j.{Logger, LoggerFactory}
import scala.xml.{Node, NodeSeq}

package object xml {

    /**
     * Implicit class to make dealing with Nodes a bit easier.
     *
     * @param node a Node.
     */
    implicit class RichXml(node: Node) {
        /**
         * This is the RichXml equivalent of the \ method.
         *
         * @param label the label/attribute to match.
         * @return a sequence of nodes whose labels/attributes match.
         */
        def /(label: String): NodeSeq = {
            if (node.label == label)
                logger.debug(s"Mild warning: selecting $label from ${node.render}")
            val result = node \ label
            val prefix = s"filter $label elements from ${renderNode(node)}: yields "
            if (result.isEmpty)
                logger.debug(s"$prefix MT list")
            else logger.debug(prefix + s"${result.size} nodes of type ${result.head.label}")
            result
        }

        def render: String = renderNode(node)
    }

    val logger: Logger = LoggerFactory.getLogger(classOf[RichXml])
}
