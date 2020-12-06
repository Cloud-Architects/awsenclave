package solutions.cloudarchitects.awsenclave;

import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * List of AMI images taken from https://docs.aws.amazon.com/enclaves/latest/user/developing-applications.html#dev-ami
 * Last access: 05 Dec 2020
 */
public final class NitroEnclavesDeveloperAmi {
    private static final Map<Region, String> DEVELOPER_AMI_NUMBERS = Collections.unmodifiableMap(
            new HashMap<Region, String>() {{
                put(Region.US_EAST_1, "ami-09a1f053468bd3dcf");
                put(Region.US_EAST_2, "ami-0bc56d1a0e5584cb4");
                put(Region.US_WEST_2, "ami-0f0124ebc1f49daf5");
                put(Region.AP_EAST_1, "ami-09b985981f1c12e8f");
                put(Region.AP_NORTHEAST_1, "ami-0022e5e5ce88aa323");
                put(Region.AP_SOUTH_1, "ami-042fc7f2cda314ab4");
                put(Region.AP_SOUTHEAST_1, "ami-0ed46cdbe7ca952fc");
                put(Region.AP_SOUTHEAST_2, "ami-005448c3d2eab01b5");
                put(Region.EU_CENTRAL_1, "ami-0acdbcca812cefd03");
                put(Region.EU_NORTH_1, "ami-031805abef5291506");
                put(Region.EU_WEST_1, "ami-09be95e78685c4142");
                put(Region.EU_WEST_2, "ami-06ddc10f4b58d3f0b");
                put(Region.EU_WEST_3, "ami-00da9426142772b8a");
                put(Region.SA_EAST_1, "ami-0b2022ccbbf6bb599");
            }});

    public static Optional<String> getImageId(Region region) {
        return Optional.ofNullable(DEVELOPER_AMI_NUMBERS.get(region));
    }
}
