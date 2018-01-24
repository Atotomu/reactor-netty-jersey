package com.atotomu.reactor.jersey;

import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.core.ScanningResourceConfig;
import com.sun.jersey.core.spi.scanning.PackageNamesScanner;

import java.util.Collection;

/**
 * @author wangtong
 * @since 1.0
 */
public class ClassPathResourceConfig extends ScanningResourceConfig {

    final String pkgNamesStr;

    /**
     * 分隔符 ,;
     *
     * @param pkgNamesStr
     */
    public ClassPathResourceConfig(String pkgNamesStr) {
        this.pkgNamesStr = pkgNamesStr;
        String[] pkgNames = getElements(new String[]{pkgNamesStr}, ResourceConfig.COMMON_DELIMITERS);
        init(new PackageNamesScanner(pkgNames));
    }

    public void addValueProviderClass(Collection<Class<?>> providers) {
        getClasses().addAll(providers);
    }
}