package uz.backenddoctor.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "seed")
@Getter
@Setter
public class DataSeedProperties {
    private boolean enabled = true;
    private int customers = 5000;
    private int products = 2000;
    private int orders = 20000;
    private int maxItemsPerOrder = 5;
}
