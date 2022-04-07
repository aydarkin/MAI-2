package utils;

import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public final class DFUtilities {
    // нахождение всех агентов, предоставляющих интересующий сервис
    public static AID[] searchService(Agent agent, String serviceName) {
        DFAgentDescription desc = new DFAgentDescription();
        ServiceDescription service_desc = new ServiceDescription();
        service_desc.setType(serviceName);
        desc.addServices(service_desc);

        SearchConstraints all = new SearchConstraints();
        all.setMaxResults((long) -1);
        try {
            DFAgentDescription[] result = DFService.search(agent, desc, all);
            AID[] agents = new AID[result.length];
            for (int i = 0; i < result.length; i++)
                agents[i] = result[i].getName();
            return agents;
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return new AID[0];
    }

    public static void register(Agent agent, ArrayList<String> services) {
        var desc = new DFAgentDescription();
        desc.setName(agent.getAID());

        for (String service : services) {
            var service_desc = new ServiceDescription();
            service_desc.setType(service);
            service_desc.setName(agent.getLocalName());
            desc.addServices(service_desc);
        }
        try {
            DFService.register(agent, desc);
        } catch (Exception e) {}
    }

    public static void register(Agent agent, String serviceName) {
        var list = new ArrayList<String>();
        list.add(serviceName);
        register(agent, list);
    }

    public static void deregister(Agent agent) {
        try {
            DFService.deregister(agent);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    public static void addService(Agent agent, String service_type) {
        DFAgentDescription desc = new DFAgentDescription();
        desc.setName(agent.getAID());
        try {
            DFAgentDescription[] agents = DFService.search(agent, desc);
            ServiceDescription service_desc = new ServiceDescription();
            service_desc.setType(service_type);
            service_desc.setName(agent.getLocalName());
            agents[0].addServices(service_desc);
            DFService.modify(agent, agents[0]);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }
}
