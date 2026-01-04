package io.github.avagodotcom.cruise.suppliers.parsers;

import io.github.avagodotcom.cruise.domain.Money;
import io.github.avagodotcom.cruise.domain.Offer;
import io.github.avagodotcom.cruise.domain.SearchRequest;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SoapOfferParser {

    public List<Offer> parse(InputStream in, SearchRequest req) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        doc.getDocumentElement().normalize();

        String vendor = text(doc, "Vendor", "VendorB");

        NodeList offers = doc.getElementsByTagName("Offer");
        List<Offer> out = new ArrayList<>();

        for (int i = 0; i < offers.getLength(); i++) {
            var offerNode = offers.item(i);

            String id = childText(offerNode, "Id");
            String ship = childText(offerNode, "ShipCode");
            LocalDate date = LocalDate.parse(childText(offerNode, "SailDate"));
            int nights = Integer.parseInt(childText(offerNode, "Nights"));
            String cabin = childText(offerNode, "CabinClass");
            long priceCents = Long.parseLong(childText(offerNode, "PriceCents"));
            String currency = childText(offerNode, "Currency");

            if (!ship.equalsIgnoreCase(req.shipCode())) continue;
            if (!date.equals(req.sailDate())) continue;
            if (!cabin.equalsIgnoreCase(req.cabinClass())) continue;

            out.add(new Offer(
                    vendor,
                    ship,
                    date,
                    nights,
                    cabin,
                    new Money(priceCents, currency),
                    Instant.now(),
                    id
            ));
        }

        return out;
    }

    private static String text(Document doc, String tag, String def) {
        NodeList nl = doc.getElementsByTagName(tag);
        if (nl.getLength() == 0) return def;
        return nl.item(0).getTextContent().trim();
    }

    private static String childText(org.w3c.dom.Node node, String tag) {
        NodeList nl = ((org.w3c.dom.Element) node).getElementsByTagName(tag);
        if (nl.getLength() == 0) throw new IllegalArgumentException("Missing tag: " + tag);
        return nl.item(0).getTextContent().trim();
    }
}
