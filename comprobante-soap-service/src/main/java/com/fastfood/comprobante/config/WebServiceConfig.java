package com.fastfood.comprobante.config;

import com.fastfood.comprobante.exception.ComprobanteException;
import com.fastfood.comprobante.soap.SoapContrato;
import com.fastfood.comprobante.soap.contract.*;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.*;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.*;
import org.springframework.ws.soap.server.endpoint.*;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.*;
import org.xml.sax.SAXParseException;
import javax.xml.namespace.QName;
import java.util.*;

@EnableWs
@Configuration
public class WebServiceConfig implements WsConfigurer {
    @Bean
    ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean
    XsdSchema comprobantesSchema() {
        return new SimpleXsdSchema(new ClassPathResource("soap/comprobantes-v1.xsd"));
    }

    @Bean(name = "comprobantes")
    DefaultWsdl11Definition comprobantesWsdl(XsdSchema comprobantesSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("ComprobantesPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(SoapContrato.NAMESPACE);
        wsdl.setSchema(comprobantesSchema);
        Properties actions = new Properties();
        actions.setProperty("generarComprobante", SoapContrato.NAMESPACE + ":generarComprobante");
        actions.setProperty("consultarComprobante", SoapContrato.NAMESPACE + ":consultarComprobante");
        wsdl.setSoapActions(actions);
        return wsdl;
    }

    @Bean
    Jaxb2Marshaller comprobantesMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(GenerarComprobanteRequest.class, GenerarComprobanteResponse.class,
                ConsultarComprobanteRequest.class, ConsultarComprobanteResponse.class);
        marshaller.setSupportDtd(false);
        marshaller.setProcessExternalEntities(false);
        return marshaller;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        PayloadValidatingInterceptor validator = new PayloadValidatingInterceptor() {
            @Override
            protected boolean handleRequestValidationErrors(MessageContext context, SAXParseException[] errors) {
                throw new ComprobanteException("VALIDACION", "El XML no cumple el contrato del comprobante.");
            }
        };
        validator.setXsdSchema(comprobantesSchema());
        validator.setValidateRequest(true);
        validator.setValidateResponse(true);
        try {
            validator.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cargar el XSD local.", ex);
        }
        interceptors.add(validator);
    }

    @Bean
    SoapFaultMappingExceptionResolver comprobantesFaultResolver() {
        SoapFaultMappingExceptionResolver resolver = new SoapFaultMappingExceptionResolver() {
            @Override
            protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception ex) {
                SoapFaultDefinition fault = new SoapFaultDefinition();
                fault.setFaultCode(ex instanceof ComprobanteException negocio && !"ERROR_INTERNO".equals(negocio.getCode())
                        ? SoapFaultDefinition.CLIENT : SoapFaultDefinition.SERVER);
                fault.setFaultStringOrReason("No se pudo procesar el comprobante.");
                return fault;
            }

            @Override
            protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
                String code = ex instanceof ComprobanteException negocio ? negocio.getCode() : "ERROR_INTERNO";
                String message = ex instanceof ComprobanteException negocio ? negocio.getMessage() : "No se pudo procesar el comprobante.";
                var detail = fault.addFaultDetail();
                detail.addFaultDetailElement(new QName(SoapContrato.NAMESPACE, "code")).addText(code);
                detail.addFaultDetailElement(new QName(SoapContrato.NAMESPACE, "message")).addText(message);
            }
        };
        resolver.setOrder(0);
        return resolver;
    }
}
