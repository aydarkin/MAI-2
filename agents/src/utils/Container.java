package utils;

import jade.core.Agent;

public final class Container {
    public static void Kill(Agent agent) {
        // удаление контейнера необходимо выполнять в
        // отдельном потоке для корректного завершения
        // агента
        Runnable killContainer = () -> {
            try {
                System.out.println("Terminating current container..");
                agent.getContainerController().kill();
            } catch (Exception e) {}
        };
        Thread t = new Thread(killContainer);
        t.start();
    }
}
