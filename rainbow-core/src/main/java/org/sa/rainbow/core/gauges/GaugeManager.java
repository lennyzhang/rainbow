package org.sa.rainbow.core.gauges;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.sa.rainbow.core.AbstractRainbowRunnable;
import org.sa.rainbow.core.Rainbow;
import org.sa.rainbow.core.RainbowComponentT;
import org.sa.rainbow.core.RainbowConstants;
import org.sa.rainbow.core.error.RainbowConnectionException;
import org.sa.rainbow.core.ports.IGaugeConfigurationPort;
import org.sa.rainbow.core.ports.IGaugeLifecycleBusPort;
import org.sa.rainbow.core.ports.IGaugeQueryPort;
import org.sa.rainbow.core.ports.IRainbowReportingPort;
import org.sa.rainbow.core.ports.RainbowPortFactory;
import org.sa.rainbow.core.util.TypedAttributeWithValue;

/**
 * The "global" gauge manager that maintains information about the global state of Rainbow gauges.
 * 
 * @author Bradley Schmerl: schmerl
 * 
 */
public class GaugeManager extends AbstractRainbowRunnable implements IGaugeLifecycleBusPort {

    static enum GMState {
        CREATED, INITIALIZED, CONFIGURED, OPERATING
    };

    public static final String ID = "Gauge Manager";


    @SuppressWarnings ("unused")
    private IGaugeLifecycleBusPort m_gaugeLifecyclePort;

    private Map<String, IGaugeConfigurationPort> configurationPorts = new HashMap<> ();
    private Map<String, IGaugeQueryPort>         queryPorts         = new HashMap<> ();
    private Map<String, GaugeInstanceDescription>                     m_gaugeDescription;
    private Set<String>                           newGaugeIds        = new HashSet<> ();
    private GMState                               m_state            = GMState.CREATED;
    private boolean                               m_waitForGauges;

    private Set<String>                           m_nonCompliantGauges = new HashSet<> ();

    public GaugeManager (GaugeDescription gd) {
        super (ID);
        m_gaugeDescription = new HashMap<> ();
        for (GaugeInstanceDescription gid : gd.instDescList ()) {
            m_gaugeDescription.put (GaugeInstanceDescription.genID (gid), gid);
        }
    }

    @Override
    public void initialize (IRainbowReportingPort port) throws RainbowConnectionException {
        super.initialize (port);
        initializeConnections ();
        // Read all the gauge information, including the deployment stuff  
        m_waitForGauges = Rainbow.getProperty (RainbowConstants.PROPKEY_WAIT_FOR_GAUGES, false);
        m_state = GMState.INITIALIZED;
    }

    protected void initializeConnections () throws RainbowConnectionException {
        m_gaugeLifecyclePort = RainbowPortFactory.createManagerLifecylePort (this);
    }

    @Override
    public void dispose () {

    }

    @Override
    protected void log (String txt) {
        m_reportingPort.info (getComponentType (), txt);
    }

    @Override
    protected void runAction () {
        switch (m_state) {
        case INITIALIZED:
            tallyGaugeCreations ();
            break;
        case OPERATING:
            checkTerminations ();
            checkHeartbeats ();
        default:
            break;
        }

    }

    private void checkTerminations () {
        Set<GaugeInstanceDescription> gaugesToTerminate = new HashSet<> ();
        synchronized (m_nonCompliantGauges) {
            if (!m_nonCompliantGauges.isEmpty ()) {
                for (Iterator<String> iterator = m_nonCompliantGauges.iterator (); iterator.hasNext ();) {
                    String gid = iterator.next ();
                    GaugeInstanceDescription gaugeDesc = m_gaugeDescription.get (gid);
                    if (gaugeDesc.beacon ().isExpired ()) {
                        gaugesToTerminate.add (gaugeDesc);
                        iterator.remove ();
                    }
                }
            }
        }
        for (GaugeInstanceDescription gaugeDesc : gaugesToTerminate) {
            forgetGauge (gaugeDesc);
        }
    }

    public void forgetGauge (GaugeInstanceDescription gaugeDesc) {
        configurationPorts.remove (gaugeDesc.id ());
        queryPorts.remove (gaugeDesc.id ());
    }

    private void checkHeartbeats () {
        synchronized (m_gaugeDescription) {
            for (Entry<String, GaugeInstanceDescription> gaugeEntry : m_gaugeDescription.entrySet ()) {
                if (gaugeEntry.getValue ().beacon ().periodElapsed ()) {
                    synchronized (m_nonCompliantGauges) {
                        if (m_nonCompliantGauges.contains (gaugeEntry.getKey ())) {
                            m_nonCompliantGauges.add (gaugeEntry.getKey ());
                        }
                        else {
                            gaugeEntry.getValue ();
                            m_reportingPort.error (RainbowComponentT.GAUGE_MANAGER,
                                    MessageFormat.format ("No heartbeat from {0}.",
                                            GaugeInstanceDescription.genID (gaugeEntry.getValue ())));
                        }
                    }

                }
            }
        }
    }

    private void tallyGaugeCreations () {
        if (m_waitForGauges) {
            if (newGaugeIds.equals (m_gaugeDescription.keySet ())) {
                // All unaccounted for gauges have been created
                for (Entry<String, IGaugeConfigurationPort> e : configurationPorts.entrySet ()) {
                    GaugeInstanceDescription gid = m_gaugeDescription.get (e.getKey ());
                    List<TypedAttributeWithValue> configParams = gid.configParams ();
                    log ("Configuring gauge: " + e.getKey ());
                    e.getValue ().configureGauge (configParams);
                }
                newGaugeIds.clear ();
                m_state = GMState.OPERATING;
            }
        }
        else {
            Set<String> gaugesToConfigure = new HashSet<> ();
            synchronized (configurationPorts) {
                gaugesToConfigure.addAll (newGaugeIds);
                if (gaugesToConfigure.isEmpty () && configurationPorts.keySet ().equals (m_gaugeDescription.keySet ())) {
                    m_state = GMState.OPERATING;
                }
            }
            for (String g : gaugesToConfigure) {
                IGaugeConfigurationPort port = configurationPorts.get (g);
                try {
                    port.configureGauge (m_gaugeDescription.get (g).configParams ());
                    synchronized (configurationPorts) {
                        newGaugeIds.remove (g);
                    }
                }
                catch (Exception e) {
                    // It's possible that a new gauge hasn't registered yet.
                    m_reportingPort.warn (getComponentType (), g + " failed to configure. Will keep trying.");
                }
            }
        }
    }

    @Override
    public void reportCreated (IGaugeIdentifier gauge) {
        // Log creation
        m_reportingPort.info (RainbowComponentT.GAUGE_MANAGER, MessageFormat.format ("Created {0}", gauge.id ()));
        log (MessageFormat.format ("Gauge Manager: A gauge was created {0}", gauge.id ()));
        // Set configuration ports
        try {
            IGaugeConfigurationPort req = RainbowPortFactory.createGaugeConfigurationPortClient (gauge);
            IGaugeQueryPort query = RainbowPortFactory.createGaugeQueryPortClient (gauge);
            synchronized (configurationPorts) {
                configurationPorts.put (gauge.id (), req);
                queryPorts.put (gauge.id (), query);
                newGaugeIds.add (gauge.id ());
                GaugeInstanceDescription gid = m_gaugeDescription.get (gauge.id ());
                if (gid != null) {
                    gid.beacon ().mark ();
                }
            }
        }
        catch (RainbowConnectionException e) {
            m_reportingPort.error (getComponentType (),
                    MessageFormat.format ("Could not create a connection to configure the gauge: {0}", gauge.id ()), e);
        }
    }

    @Override
    public void reportDeleted (IGaugeIdentifier gauge) {
        m_reportingPort.info (RainbowComponentT.GAUGE_MANAGER, MessageFormat.format ("Deleted {0}", gauge.id ()));
        synchronized (configurationPorts) {
            configurationPorts.remove (gauge.id ());
            queryPorts.remove (gauge.id ());
        }
    }

    @Override
    public void reportConfigured (IGaugeIdentifier gauge, List<TypedAttributeWithValue> configParams) {
        log (gauge.id () + " was configured.");
    }

    @Override
    public void sendBeacon (IGaugeIdentifier gauge) {
        GaugeInstanceDescription gid = m_gaugeDescription.get (gauge.id ());
        if (gid != null) {
            gid.beacon ().mark ();
        }

    }

    @Override
    protected RainbowComponentT getComponentType () {
        return RainbowComponentT.GAUGE_MANAGER;
    }

}
