package ora4mas.nopl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import cartago.ArtifactId;
import cartago.LINK;
import cartago.OPERATION;
import cartago.OperationException;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.PredicateIndicator;
import jason.util.Config;
import moise.common.MoiseException;
import npl.DynamicFactsProvider;
import npl.NPLInterpreter;
import npl.NormativeFailureException;
import npl.NormativeProgram;
import npl.parser.ParseException;
import npl.parser.nplp;
import ora4mas.nopl.oe.CollectiveOE;

/**
 * Artifact to manage a normative program (NPL)
 * <br/><br/>
 * 
 * <b>Operations</b> (see details in the methods list below):
 * <ul>
 * <li>load a NPL program
 * <li>addFact
 * <li>removeFact
 * </ul>
 * 
 * <b>Observable properties</b>:
 * <ul>
 * <li>obligation(ag,reason,goal,deadline): current active obligations.</br>
 *     e.g. <code>obligation(bob,n4,committed(ag2,mBib,s1),1475417322254)</code>
 * </ul>
 * 
 * <b>Signals</b> the same signals of SchemeBoard.
 * </ul>
 * 
 * @see SchemeBoard
 * @author Jomi
 */
public class NormativeBoard extends OrgArt {

    protected Map<String, DynamicFactsProvider> dynProviders = new HashMap<String, DynamicFactsProvider>();
    
    protected Logger logger = Logger.getLogger(NormativeBoard.class.getName());

    /**
     * Initialises the normative artifact
     */
    public void init() {
        oeId = getCreatorId().getWorkspaceId().getName();
        String nbId = getId().getName();

        nengine = new NPLInterpreter();
        nengine.init();
        installNormativeSignaler();

        if (! "false".equals(Config.get().getProperty(Config.START_WEB_OI))) {
            WebInterface w = WebInterface.get();
            try {
                w.registerOEBrowserView(oeId, "/norm/", nbId, this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }         
    }
    
    /**
     * Loads a normative program
     * 
     * @param nplProgram       a string with the NPL program (or a file name)
     *
     * @throws ParseException  if the OS file is not correct
     * @throws MoiseException  if schType was not specified
     */
    @OPERATION @LINK public void load(String nplProgram) throws MoiseException, ParseException {
        NormativeProgram p = new NormativeProgram();

        File f = new File(nplProgram);
        try {
            if (f.exists()) {
                new nplp(new FileReader(nplProgram)).program(p, this);
            } else {
                new nplp(new StringReader(nplProgram)).program(p, this);
            }
        } catch (FileNotFoundException e) {
        } catch (ParseException e) {
            logger.warning("error parsing \n"+nplProgram);
            e.printStackTrace();
            throw e;
        }
        nengine.loadNP(p.getRoot());
        
        if (gui != null) {
            gui.setNormativeProgram(getNPLSrc());
        }
    }

    @OPERATION public void debug(String kind) throws Exception {
        super.debug(kind, "Norm Board");
    }
    
    @OPERATION void addFact(String f) throws jason.asSyntax.parser.ParseException, NormativeFailureException {
        nengine.addFact(ASSyntax.parseLiteral(f));
        nengine.verifyNorms();
        updateGuiOE();
    }
    
    @OPERATION void removeFact(String f) throws jason.asSyntax.parser.ParseException, NormativeFailureException {
        nengine.removeFact(ASSyntax.parseLiteral(f));
        nengine.verifyNorms();
        updateGuiOE();
    }

    @LINK void updateDFP(String id, DynamicFactsProvider p) throws NormativeFailureException {
        dynProviders.put(id, p);
        nengine.verifyNorms();
        updateGuiOE();
    }
    

    @OPERATION @LINK void doSubscribeDFP(String artName) throws OperationException {
        ArtifactId aid = lookupArtifact(artName);
        execLinkedOp(aid, "subscribeDFP", getId());
    }
    
    
    @Override
    public String getDebugText() {
        boolean first = true;
        StringBuilder out = new StringBuilder(super.getDebugText());
        for (DynamicFactsProvider p: dynProviders.values()) {
            System.out.println(p);
            if (p instanceof CollectiveOE) {
                for (Literal l: ((CollectiveOE)p).transform()) {
                    if (first) {
                        out.append("\n\n** dynamic facts:\n");
                        first = false;
                    }
                    out.append("     "+l+"\n");
                }
            }
        }
        return out.toString();
    }
    
    @Override
    public String getNPLSrc() {
        return nengine.getNormsString();
    }
    
    protected String getStyleSheetName() {
        return null;                 
    }
    
    public Element getAsDOM(Document document) {
        return nengine.getAsDOM(document);
    }
    
    // DFP methods
    
    public boolean isRelevant(PredicateIndicator pi) {
        for (DynamicFactsProvider p: dynProviders.values())
            if (p.isRelevant(pi))
                return true;
        return false;
    }
    
    public Iterator<Unifier> consult(Literal l, Unifier u) {
        for (DynamicFactsProvider p: dynProviders.values())
            if (p.isRelevant(l.getPredicateIndicator())) 
                return p.consult(l, u);
        return null;
    }

}
