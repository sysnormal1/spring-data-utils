package com.sysnormal.commons.spring.spring_data_utils.configs;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;

/**
 * Class thet can be used for register a entity manager factory in database config as readonly, calling register method.
 *
 *
 * @author Alencar
 * @version 1.0.0
 */
public class DefaultReadOnlyEventListener implements PreInsertEventListener, PreUpdateEventListener, PreUpsertEventListener, PreDeleteEventListener, PreCollectionUpdateEventListener, PreCollectionRecreateEventListener, PreCollectionRemoveEventListener {


    public static final String MSG_ERROR = "operation is not allowed in this connection";


    @Override
    public boolean onPreInsert(PreInsertEvent event) {
        throw new HibernateException("Insert " + MSG_ERROR);
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {

        String entityName = event.getEntity().getClass().getSimpleName(); // Nome da classe Java
        //String tableName = event.getPersister().getTableName(); // Nome real da tabela no banco

        // Valores antigos e novos
        Object[] oldState = event.getOldState();
        Object[] newState = event.getState();
        String[] propertyNames = event.getPersister().getPropertyNames();

        // Exemplo de log
        System.out.println("tried update on table: " + entityName);
        for (int i = 0; i < propertyNames.length; i++) {
            System.out.println(
                    propertyNames[i] + " : " + oldState[i] + " -> " + newState[i]
            );
        }



        throw new UnsupportedOperationException("Update " + MSG_ERROR);
    }

    @Override
    public boolean onPreDelete(PreDeleteEvent event) {
        throw new UnsupportedOperationException("Delete " + MSG_ERROR);
    }


    @Override
    public boolean onPreUpsert(PreUpsertEvent preUpsertEvent) {
        throw new HibernateException("Upsert " + MSG_ERROR);
    }

    @Override
    public void onPreRecreateCollection(PreCollectionRecreateEvent preCollectionRecreateEvent) {
        throw new HibernateException("Upsert " + MSG_ERROR);
    }

    @Override
    public void onPreRemoveCollection(PreCollectionRemoveEvent preCollectionRemoveEvent) {
        throw new HibernateException("Delete " + MSG_ERROR);
    }

    @Override
    public void onPreUpdateCollection(PreCollectionUpdateEvent preCollectionUpdateEvent) {
        throw new HibernateException("Upsert " + MSG_ERROR);
    }

    public static void register(EntityManagerFactory emf) {
        SessionFactoryImplementor sfi = emf.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry = sfi.getServiceRegistry().getService(EventListenerRegistry.class);
        DefaultReadOnlyEventListener defaultReadOnlyEventListener = new DefaultReadOnlyEventListener();
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(defaultReadOnlyEventListener);
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(defaultReadOnlyEventListener);
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(defaultReadOnlyEventListener);
        registry.getEventListenerGroup(EventType.PRE_UPSERT).appendListener(defaultReadOnlyEventListener);
        registry.getEventListenerGroup(EventType.PRE_COLLECTION_UPDATE).appendListener(defaultReadOnlyEventListener);
        registry.getEventListenerGroup(EventType.PRE_COLLECTION_REMOVE).appendListener(defaultReadOnlyEventListener);
        registry.getEventListenerGroup(EventType.PRE_COLLECTION_RECREATE).appendListener(defaultReadOnlyEventListener);
    }
}