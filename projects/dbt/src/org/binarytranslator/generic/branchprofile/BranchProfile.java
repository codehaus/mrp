/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.binarytranslator.generic.branchprofile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Object capturing branches and jumps so that traces can avoid terminating on
 * branches whose destinations aren't known
 */
public class BranchProfile {
  
  public enum BranchType {
    INDIRECT_BRANCH,
    DIRECT_BRANCH,
    CALL,
    RETURN
  }

  /** A set of procedure information */
  private final SortedMap<Integer, ProcedureInformation> procedures;

  /** A set of switch like branchs sites and their destinations */
  private final SortedMap<Integer, BranchInformation> branchSites;

  /** Global branch information */
  private static BranchProfile global;
  
  /**
   * Stores information on a single branch location. */
  private final static class BranchInformation {
     
    /** How often the branch instruction has been executed. */
    private int executionCount;
    
    /** The target addresses of the branch and how often they have been branched to. */
    private final Map<Integer, Integer> destinationsAndFrequencies;
    
    public BranchInformation() {
      this.destinationsAndFrequencies = new HashMap<Integer, Integer>();
    }
    
    public BranchInformation(HashMap<Integer, Integer> destinationsAndFrequencies) {
      this.destinationsAndFrequencies = destinationsAndFrequencies;
      executionCount = 0;
      
      for (Entry<Integer, Integer> target : destinationsAndFrequencies.entrySet()) {
        executionCount += target.getValue();
      }
    }
    
    public void profile(int target) {
      executionCount++;
      Integer targetCount = destinationsAndFrequencies.get(target);
      
      targetCount = (targetCount == null) ? 1 : targetCount + 1;
      destinationsAndFrequencies.put(target, targetCount);
    }
    
    /** Returns a list of addresses that this branch jumps to. */
    public Set<Integer> getTargets() {
      return destinationsAndFrequencies.keySet();
    }
    
    public Map<Integer, Integer> getTargetsAndFrequencies() {
      return destinationsAndFrequencies;
    }
    
    public void registerTargetSite(int target) {
      if (destinationsAndFrequencies.get(target) == null)
        destinationsAndFrequencies.put(target, 0);
    }
    
    /**
     * Loads a {@link BranchInformation} object from an XML element, given that the object
     * was previously persisted by {@link #toXML(Document, Element)}.
     * @param node
     *  The XML element that had been provided to {@link #toXML(Document, Element)}.
     * @throws IOException 
     */
    public static BranchInformation fromXML(Element node) throws IOException {
      HashMap<Integer, Integer> targetsAndFrequencies = new HashMap<Integer, Integer>();
      
      for (int n = 0; n < node.getChildNodes().getLength(); n++) {
        Node target = node.getChildNodes().item(n);
        
        if (target.getNodeType() != Node.ELEMENT_NODE)
          continue;
        
        if (!target.getNodeName().equals("target"))
          throw new IOException("File is not a valid XML branch profile.");
        
        int targetAddress = Integer.parseInt(((Element)target).getAttribute("address"));
        int branchCount = Integer.parseInt(((Element)target).getAttribute("branchCount"));
        targetsAndFrequencies.put(targetAddress, branchCount);
      }
      
      return new BranchInformation(targetsAndFrequencies);
    }
    
    public void toXML(Document doc, Element parentNode) {
      Element branchInfo = parentNode;
      
      for (Entry<Integer, Integer> branchTarget : destinationsAndFrequencies.entrySet()) {
        int targetAddress = branchTarget.getKey();
        int branchCount = branchTarget.getValue();
        
        Element branchTargetElement = doc.createElement("target");
        branchInfo.appendChild(branchTargetElement);
        branchTargetElement.setAttribute("address", Integer.toString(targetAddress));
        branchTargetElement.setAttribute("branchCount", Integer.toString(branchCount));
      }
    }
  }

  /**
   * Constructor has 2 functions: (1) when making a local trace we don't want to
   * consider as many procedure return points as may be known for the full
   * program. (2) making sure switch like branches are all recorded globally
   */
  public BranchProfile() {
    if (global == null) {
      global = this;
      branchSites = new TreeMap<Integer, BranchInformation>();
    } 
    else {
      branchSites = global.branchSites;
    }
    procedures = new TreeMap<Integer, ProcedureInformation>();
  }

  /**
   * Register a call (branch and link) instruction
   * 
   * @param pc
   *  the address of the branch instruction
   * @param dest
   *  the destination of the branch instruction
   * @param ret
   *  the address that will be returned to
   */
  public void registerCallSite(int pc, int dest, int ret) {
    ProcedureInformation procedure = procedures.get(dest);
    
    if (procedure != null) {
      procedure.registerCall(pc, ret);
    } else {
      procedure = new ProcedureInformation(pc, ret, dest);
      procedures.put(dest, procedure);
    }
    
    registerDynamicBranchSite(pc, dest);
  }
  
  /**
   * Register a function return.
   * 
   * @param pc
   *          the address of the return instruction
   */
  public void registerReturnSite(int pc) {
    
    ProcedureInformation procedure = getLikelyProcedure(pc);
    
    if (procedure != null) {
      procedure.registerReturn(pc);
    }
  }

  /**
   * Register a function return.
   * 
   * @param pc
   *          the address of the return  instruction
   * @param lr
   *          the return address (value of the link register)
   */
  public void registerReturnSite(int pc, int lr) {
    
    ProcedureInformation procedure = getLikelyProcedure(pc);
    
    if (procedure != null) {
      procedure.registerReturn(pc);
    }
    
    registerDynamicBranchSite(pc, lr);
  }
  
  /**
   * Appends a branch from <code>origin</code> to <code>target</code> to the branch profile.
   * 
   * @param origin
   *  The address that the branch is taking place from.
   * @param target
   *  The branch target address.
   */
  public void registerDynamicBranchSite(int origin, int target) {
    //Perform the general branch registration, too
    BranchInformation branch = branchSites.get(origin);
    
    if (branch != null) {
      branch.registerTargetSite(target);
      // This destination address is already registered
      return;
    } 
    else {
      branch = new BranchInformation();
      branch.registerTargetSite(target);
      branchSites.put(origin, branch);
    }
  }
  
  /**
   * Returns the probability of a branch from <code>origin</code> to <code>target</code>.
   * 
   * @param origin
   *  The address at which the branch is taking place.
   * @param target
   *  The address to which the branch is taking place.
   * @return
   *  The probability of the branch as a value between 0 and 1. The function returns -1, if the probability
   *  cannot be estimated.
   */
  public float getBranchProbability(int origin, int target) {
    BranchInformation branch = branchSites.get(origin);
    
    if (branch == null || branch.executionCount == 0) {
      return -1f;
    }
    
    Integer branchesToTarget = branch.getTargetsAndFrequencies().get(target);
    
    if (branchesToTarget == null) {
      return 0f;
    }
    
    return branchesToTarget / (float)branch.executionCount;
  }

  /**
   * Given an address within a procedure, returns the (most likely) procedure
   * 
   * @param pc
   *          a location within the procedure
   * @return corressponding procedure information
   */
  private ProcedureInformation getLikelyProcedure(int pc) {
    if (procedures.size() > 0) {
      SortedMap<Integer, ProcedureInformation> priorProcedures = procedures.headMap(pc);
      if (priorProcedures.size() > 0) {
        Integer procedureEntry = priorProcedures.lastKey();
        return procedures.get(procedureEntry);
      }
    }
    return null;
  } 
  
  /**
   * Records that a branch from <code>origin</code> to <code>target</code> has been observed while the program is running.
   * 
   * @param origin
   *  The address from which the branch took place.
   * @param target
   *  The address to which it took place.
   */
  public void profileBranch(int origin, int target) {
 
    BranchInformation branch = branchSites.get(origin);
    
    if (branch == null) {
      branch = new BranchInformation();
      branchSites.put(origin, branch);
    }
    
    branch.profile(target);
  }
  
  /**
   * Returns a list of known branch targets for the branch at address <code>pc</code>.
   * 
   * @param pc
   *  The address where the branch originates from.
   * @return
   *  A list of known target addresses for this branch. It is not critical to the functionality of the 
   *  translated binary, if this list is not complete. 
   */
  public Set<Integer> getKnownBranchTargets(int pc) {
    BranchInformation branch = branchSites.get(pc); 
    return (branch == null) ? null : branch.getTargets();
  }
  
  /**
   * Loads a branch profile from a file.
   * 
   * @param filename
   *  The name of the file that the branch profile is loaded from.
   * @throws IOException
   *  An exception that is thrown if an error occurs reading that file.
   */
  public void loadFromXML(String filename) throws IOException {

    //clear previous profile data
    procedures.clear();
    branchSites.clear();
    
    //create a XML document builder and an XML document
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    Document doc;
    try {
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      doc = docBuilder.parse(filename);
    } catch (ParserConfigurationException e) {
      throw new RuntimeException("Error creating DocumentBuilder instance to read an XML file.");
    } catch (SAXException e) {
      throw new IOException("File " + filename + " is not a valid XML file.");
    }
    
    if (DBT.VerifyAssertions) DBT._assert(doc != null);
    
    //Check the root-name of the document
    Element root = doc.getDocumentElement();
    if (!root.getNodeName().equals("branch-profile"))
      throw new IOException("File is not a valid XML branch profile.");
    
    //interate over the main document, parsing the different sections
    for (int i = 0; i < root.getChildNodes().getLength(); i++) {
      Node node = root.getChildNodes().item(i);
      String nodeName = node.getNodeName();
      
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      
      if (nodeName.equals("branches")) {
      
        //this is the section which contains the branch information.
        for (int n = 0; n < node.getChildNodes().getLength(); n++) {
          Node branchNode = node.getChildNodes().item(n);
          
          if (branchNode.getNodeType() != Node.ELEMENT_NODE)
            continue;
          
          if (!branchNode.getNodeName().equals("branch"))
            throw new IOException("File is not a valid XML branch profile.");
          
          int address = Integer.parseInt(((Element)branchNode).getAttribute("address"));
          BranchInformation branchInfo = BranchInformation.fromXML((Element)branchNode);
          branchSites.put(address, branchInfo);
        }
      }
      else
      if (nodeName.equals("procedures")) {
        
        //this is the section with procedure information
        for (int n = 0; n < node.getChildNodes().getLength(); n++) {
          Node procedureInfo = node.getChildNodes().item(n);
          
          if (procedureInfo.getNodeType() != Node.ELEMENT_NODE)
            continue;
          
          ProcedureInformation pi = ProcedureInformation.fromXML((Element)procedureInfo);
          procedures.put(pi.getEntryAddress(), pi);
        }
      }
      else {
        throw new IOException("This is not a valid XML branch profile.");
      }
    }
  }
  
  /**
   * Saves the branch profile of the current process space to the give file in XML format.
   * 
   * @param filename
   *  The name of the file to which the branch profile is saved. 
   * @throws IOException
   *  Thrown if there is an error while creating the file.
   */
  public void saveAsXML(String filename) throws IOException {
    
    FileOutputStream outputStream;
    
    try {
      File f = new File(filename);
      
      if (!f.exists())
        f.createNewFile();
      
      outputStream = new FileOutputStream(f);
    }
    catch (FileNotFoundException e) {
      //this should not happen, as we just created the file
      throw new IOException("Error creating file: " + filename);
    }
    
    //Create an XML representation of the branch profile
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder;
    try {
      docBuilder = docBuilderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IOException("Error creating parser to produce XML document.");
    }
    Document doc = docBuilder.newDocument();
    
    Element root = doc.createElement("branch-profile");
    root.setAttribute("application", DBT_Options.executableFile);
    doc.appendChild(root);
    
    Element branchesElement = doc.createElement("branches");
    root.appendChild(branchesElement);

    for (Entry<Integer, BranchInformation> branch : branchSites.entrySet()) {
      
      int address = branch.getKey();
      BranchInformation branchInfo = branch.getValue();
      
      Element branchSiteElement = doc.createElement("branch");
      branchesElement.appendChild(branchSiteElement);
      branchSiteElement.setAttribute("address", Integer.toString(address));
      branchInfo.toXML(doc, branchSiteElement);
    }
    
    Element proceduresElement = doc.createElement("procedures");
    root.appendChild(proceduresElement);
    
    for (ProcedureInformation procedure : procedures.values()) {
      Element procedureElement = doc.createElement("procedure");
      proceduresElement.appendChild(procedureElement);
      procedure.toXML(doc, procedureElement);
    }
    
    //Output the resulting XML document
    TransformerFactory tFactory = TransformerFactory.newInstance();
    Transformer transformer;
    try {
      transformer = tFactory.newTransformer();
      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(outputStream);
      transformer.transform(source, result);
      outputStream.close();
      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
