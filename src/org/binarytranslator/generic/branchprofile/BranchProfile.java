/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branchprofile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

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
  private final SortedMap<Integer, Set<Integer>> branchSitesAndDestinations;

  /** Global branch information */
  private static BranchProfile global;

  /**
   * Constructor has 2 functions: (1) when making a local trace we don't want to
   * consider as many procedure return points as may be known for the full
   * program. (2) making sure switch like branches are all recorded globally
   */
  public BranchProfile() {
    if (global == null) {
      global = this;
      branchSitesAndDestinations = new TreeMap<Integer, Set<Integer>>();
    } 
    else {
      branchSitesAndDestinations = global.branchSitesAndDestinations;
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
  public void registerCall(int pc, int dest, int ret) {
    ProcedureInformation procedure = procedures.get(dest);
    
    if (procedure != null) {
      procedure.registerCall(pc, ret);
    } else {
      procedure = new ProcedureInformation(pc, ret, dest);
      procedures.put(dest, procedure);
    }
    
    registerBranch(pc, dest);
  }

  /**
   * Register a function return.
   * 
   * @param pc
   *          the address of the branch instruction
   * @param lr
   *          the return address (value of the link register)
   */
  public void registerReturn(int pc, int lr) {
    
    ProcedureInformation procedure = getLikelyProcedure(pc);
    
    if (procedure != null) {
      procedure.registerReturn(pc, lr);
    }
    
    registerBranch(pc, lr);
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
   * Registers a branch from the address <code>origin</code> to the address <code>target</code>.
   * The type of branch is determined by <code>type</code>, which is an ordinal from the 
   * {@link BranchType} enum.
   * 
   * @param origin
   *  The address from which the branch occurs. 
   * @param target
   *  The address to which the program is branching.
   * @param type
   *  The most likely type of the branch. This is taken from the {@link BranchType} enum. 
   */
  public void registerBranch(int origin, int target, BranchType type) {
      
    switch (type) {
    case CALL:
      throw new RuntimeException("Use the more specific registerCall() for these cases.");

    case RETURN:
      registerReturn(origin, target);
      break;
      
    default:
      registerBranch(origin, target);
    }    
  }

  /**
   * Appends a recorded branch from <code>origin</code> to <code>target</code> to the branch profile.
   * 
   * @param origin
   *  The address that the branch is taking place from.
   * @param target
   *  The branch target address.
   */
  private void registerBranch(int origin, int target) {
    //Perform the general branch registration, too
    Set<Integer> dests = branchSitesAndDestinations.get(origin);
    
    if (dests != null && dests.contains(target)) {
      // This destination address is already registered
      return;
    } 
    else {
      dests = new HashSet<Integer>();
      branchSitesAndDestinations.put(origin, dests);
    }
    
    dests.add(target);
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
    return branchSitesAndDestinations.get(pc);
  }
  
  public void loadFromXML(String filename) throws IOException {

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
    
    Element root = doc.getDocumentElement();

    if (!root.getNodeName().equals("branch-profile"))
      throw new IOException("File is not a valid XML branch profile.");
    
    Node branches = null;
    
    for (int i = 0; i < root.getChildNodes().getLength(); i++) {
      Node node = root.getChildNodes().item(0);
      
      if (node.getNodeName().equals("branches")) {
        branches = node;
        break;
      }
    }
    
    if (branches == null)
      throw new IOException("File is not a valid XML branch profile.");
    
    for (int i = 0; i < branches.getChildNodes().getLength(); i++) {
      Node siteNode = branches.getChildNodes().item(i);
      
      if (!siteNode.getNodeName().equals("origin") || siteNode.getNodeType() != Node.ELEMENT_NODE)
        throw new IOException("File is not a valid XML branch profile.");
      
      int pc = Integer.parseInt(((Element)siteNode).getAttribute("address"));
      
      for (int n = 0; n < siteNode.getChildNodes().getLength(); n++) {
        Node target = siteNode.getChildNodes().item(n);
        
        if (!target.getNodeName().equals("target") || target.getNodeType() != Node.ELEMENT_NODE)
          throw new IOException("File is not a valid XML branch profile.");
        
        int targetAddress = Integer.parseInt(((Element)target).getAttribute("address"));
        registerBranch(pc, targetAddress);
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
    
    for (int pc : branchSitesAndDestinations.keySet()) {
      Element branchSiteElement = doc.createElement("origin");
      branchesElement.appendChild(branchSiteElement);
      branchSiteElement.setAttribute("address", Integer.toString(pc));
      
      for (int target : getKnownBranchTargets(pc)) {
        Element branchTargetElement = doc.createElement("target");
        branchSiteElement.appendChild(branchTargetElement);
        branchTargetElement.setAttribute("address", Integer.toString(target));
      }
    }
    
    //Output the resulting XML document
    TransformerFactory tFactory = TransformerFactory.newInstance();
    Transformer transformer;
    try {
      transformer = tFactory.newTransformer();
      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(outputStream);
      transformer.transform(source, result);
      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
