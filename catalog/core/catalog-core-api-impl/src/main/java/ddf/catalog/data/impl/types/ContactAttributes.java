/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.data.impl.types;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.impl.AttributeDescriptorImpl;
import ddf.catalog.data.impl.BasicTypes;
import ddf.catalog.data.types.Contact;

/**
 * This class provides attributes that reflect metadata about different kinds of
 * people/groups/units/organizations associated with the metacard. This consists
 * of the creator, contributor, publisher and the point of contact.
 */
public class ContactAttributes implements Contact, MetacardType {

    private static final Set<AttributeDescriptor> DESCRIPTORS;

    private static final String NAME = "contact";

    static {
        Set<AttributeDescriptor> descriptors = new HashSet<>();
        descriptors.add(new AttributeDescriptorImpl(CONTRIBUTOR_ADDRESS,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CONTRIBUTOR_EMAIL,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CONTRIBUTOR_NAME,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CONTRIBUTOR_PHONE,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CREATOR_ADDRESS,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CREATOR_EMAIL,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CREATOR_NAME,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(CREATOR_PHONE,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(POINT_OF_CONTACT_ADDRESS,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(POINT_OF_CONTACT_EMAIL,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(POINT_OF_CONTACT_NAME,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(POINT_OF_CONTACT_PHONE,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(PUBLISHER_ADDRESS,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(PUBLISHER_EMAIL,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(PUBLISHER_NAME,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        descriptors.add(new AttributeDescriptorImpl(PUBLISHER_PHONE,
                true /* indexed */,
                true /* stored */,
                true /* tokenized */,
                true /* multivalued */,
                BasicTypes.STRING_TYPE));
        DESCRIPTORS = Collections.unmodifiableSet(descriptors);
    }

    @Override
    public Set<AttributeDescriptor> getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public AttributeDescriptor getAttributeDescriptor(String name) {
        for (AttributeDescriptor attributeDescriptor : DESCRIPTORS) {
            if (attributeDescriptor.getName()
                    .equals(name)) {
                return attributeDescriptor;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }
}


