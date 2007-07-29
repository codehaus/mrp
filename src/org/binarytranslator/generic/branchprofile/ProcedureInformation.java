/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branchprofile;

import java.util.HashSet;
import java.util.Comparator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Objects capturing information about what looks like a method
 */
class ProcedureInformation {
  /**
   * Entry point to the procedure
   */
  private final int entry;

  /**
   * Set of locations that call the procedure and the corressponding return
   * address
   */
  private HashSet<CallAndReturnAddress> callSitesAndReturnAddresses;

  /**
   * Set of locations within the procedure that return
   */
  private HashSet<Integer> returnSites;

  /**
   * Comparator for procedure information
   */
  @SuppressWarnings("unused")
  private static final class ProcedureInformationComparator implements
      Comparator {
    /**
     * Compare two procedure information objects
     */
    public int compare(Object o1, Object o2) {
      return ((ProcedureInformation) o1).entry
          - ((ProcedureInformation) o2).entry;
    }
  }
  
  private ProcedureInformation(int entry) {
    this.entry = entry;
  }

  /**
   * Constructor
   * 
   * @param entry
   *          starting address of the procedure
   * @param callSite
   *          the address calling the procedure
   * @param returnAddress
   *          the corresponding return address
   */
  ProcedureInformation(int entry, int callSite, int returnAddress) {
    this.entry = entry;
    callSitesAndReturnAddresses = new HashSet<CallAndReturnAddress>();
    callSitesAndReturnAddresses.add(new CallAndReturnAddress(callSite,
        returnAddress));
  }

  /**
   * Register a call (branch and link) instruction
   * 
   * @param pc
   *          the address of the branch instruction
   * @param ret
   *          the address that will be returned to
   */
  public void registerCall(int pc, int ret) {
    CallAndReturnAddress call_tuple = new CallAndReturnAddress(pc, ret);
    if (!callSitesAndReturnAddresses.contains(call_tuple)) {
      callSitesAndReturnAddresses.add(call_tuple);
    }
  }

  /**
   * Register a return (branch to link register) instruction
   * 
   * @param pc
   *          the address of the branch instruction
   */
  public void registerReturn(int pc) {
    if (returnSites == null) {
      returnSites = new HashSet<Integer>();
    }
    
    returnSites.add(new Integer(pc));
  }
  
  /**
   * Serializes this object to an XML document.
   *  
   * @param doc
   *  The XML document that the object shall be serialized to.
   * @param parentNode
   *  The node within <code>doc</code> that the object shall be serialized to.
   */
  public void toXML(Document doc, Element parentNode) {
    Element procedure = parentNode;
    procedure.setAttribute("entry", Integer.toString(entry));
    
    Element callSites = doc.createElement("callsites");
    for (CallAndReturnAddress caller : callSitesAndReturnAddresses) {
      Element callerNode = doc.createElement("call");
      callerNode.setAttribute("from", Integer.toString(caller.getCallSite()));
      callerNode.setAttribute("return", Integer.toString(caller.getReturnAddress()));
      callSites.appendChild(callerNode);
    }
    procedure.appendChild(callSites);
    
    Element returnSites = doc.createElement("returnsites");
    for (Integer returnSite : this.returnSites) {
      Element returnSiteNode = doc.createElement("return");
      returnSiteNode.setAttribute("at", returnSite.toString());
      returnSites.appendChild(returnSiteNode);
    }
    procedure.appendChild(returnSites);
  }
  
  /**
   * Loads a {@link ProcedureInformation} object from an XML element, given that the object
   * was previously persisted by {@link #toXML(Document, Element)}.
   * @param node
   *  The XML element that had been provided to {@link #toXML(Document, Element)}.
   */
  public static ProcedureInformation fromXML(Element node) {
    
    ProcedureInformation pi = new ProcedureInformation(Integer.parseInt(node.getAttribute("entry")));
    
    for (int i = 0; i < node.getChildNodes().getLength(); i++) {
      Node childNode = node.getChildNodes().item(i);
      
      //skip non-element nodes
      if (childNode.getNodeType() != Node.ELEMENT_NODE)
        continue;
      
      if (childNode.getNodeName().equals("callsites")) {
        //parse call sites
        for (int n = 0; n < childNode.getChildNodes().getLength(); n++) {
          Node callsite = childNode.getChildNodes().item(n);
          
          //skip non-element nodes
          if (callsite.getNodeType() != Node.ELEMENT_NODE)
            continue;
          
          if (callsite.getNodeName().equals("call"))
            throw new Error("The given XML node is not a valid ProcedureInformation entity.");
          
          int callFrom = Integer.parseInt(((Element)callsite).getAttribute("from"));
          int callReturn = Integer.parseInt(((Element)callsite).getAttribute("return"));
          
          pi.callSitesAndReturnAddresses.add(new CallAndReturnAddress(callFrom, callReturn));
        }
      }
      else if (childNode.getNodeName().equals("returnsites")) {
        //parse return sites
        for (int n = 0; n < childNode.getChildNodes().getLength(); n++) {
          Node callsite = childNode.getChildNodes().item(n);
          
          //skip non-element nodes
          if (callsite.getNodeType() != Node.ELEMENT_NODE)
            continue;
          
          if (callsite.getNodeName().equals("return"))
            throw new Error("The given XML node is not a valid ProcedureInformation entity.");
          
          int returnAt = Integer.parseInt(((Element)callsite).getAttribute("at"));
          pi.returnSites.add(returnAt);
        }
      }
      else {
        throw new Error("The given XML node is not a valid ProcedureInformation entity.");
      }      
    }

    return pi;
  }
}
