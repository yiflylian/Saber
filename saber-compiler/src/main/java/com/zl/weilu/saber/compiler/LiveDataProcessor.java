package com.zl.weilu.saber.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.zl.weilu.saber.annotation.LiveData;
import com.zl.weilu.saber.compiler.utils.BaseProcessor;
import com.zl.weilu.saber.compiler.utils.ClassEntity;
import com.zl.weilu.saber.compiler.utils.FieldEntity;
import com.zl.weilu.saber.compiler.utils.StringUtils;

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public class LiveDataProcessor extends BaseProcessor {

    @Override
    protected Class[] getSupportedAnnotations() {
        return new Class[]{LiveData.class};
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<String, ClassEntity> map = entityHandler.handlerElement(roundEnv, this);
        for (Map.Entry<String, ClassEntity> item : map.entrySet()) {
            entityHandler.generateCode(brewViewModel(item));
        }
        return true;
    }

    private JavaFile brewViewModel(Map.Entry<String, ClassEntity> item) {

        ClassEntity classEntity = item.getValue();
        /*类名*/
        String className = classEntity.getElement().getSimpleName().toString() + "ViewModel";

        ClassName mutableLiveDataClazz = ClassName.get("android.arch.lifecycle", "MutableLiveData");
        ClassName viewModelClazz = ClassName.get("android.arch.lifecycle", "ViewModel");

        TypeSpec.Builder builder = TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .superclass(viewModelClazz);


        Map<String, FieldEntity> fields = classEntity.getFields();

        for (FieldEntity fieldEntity : fields.values()){

            FieldSpec field;
            String fieldName = StringUtils.upperCase(fieldEntity.getFieldName());

            String l = fieldEntity.getTypeString();
            ParameterizedTypeName liveDataTypeName = null;
            
            ClassName mClazz = null;

            int i = l.indexOf("<");
            if (i < 0){
                mClazz = ClassName.bestGuess(l);

            }else {
                String type = l.substring(0, i);

                int e = l.lastIndexOf(">");

                String[] types = l.substring(i + 1, e).split(",");
                
                if (types.length == 1){
                    liveDataTypeName = ParameterizedTypeName.get(ClassName.bestGuess(type), ClassName.bestGuess(types[0]));
                }

                if (types.length == 2){
                    liveDataTypeName = ParameterizedTypeName.get(ClassName.bestGuess(type), ClassName.bestGuess(types[0]), ClassName.bestGuess(types[1]));
                }
                
            }

            ParameterizedTypeName typeName = ParameterizedTypeName.get(mutableLiveDataClazz, mClazz == null ? liveDataTypeName : mClazz);

            field = FieldSpec.builder(typeName, "m" + fieldName, Modifier.PRIVATE)
//                    .initializer("new $T<>()", mutableLiveDataClazz)
                    .build();

            MethodSpec getMethod = MethodSpec
                    .methodBuilder("get" + fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(field.type)
                    .beginControlFlow("if (m$L == null)", fieldName)
                    .addStatement("m$L = new MutableLiveData<>()", fieldName)
                    .endControlFlow()
                    .addStatement("return m$L", fieldName)
                    .build();

            MethodSpec getValue = MethodSpec
                    .methodBuilder("get" + fieldName + "Value")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(mClazz == null ? liveDataTypeName : mClazz)
                    .addStatement("return get$L().getValue()", fieldName)
                    .build();

            MethodSpec setMethod = MethodSpec
                    .methodBuilder("set" + fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(mClazz == null ? liveDataTypeName : mClazz, "mValue")
                    .beginControlFlow("if (this.m$L == null)", fieldName)
                    .addStatement("return")
                    .endControlFlow()
                    .addStatement("this.m$L.setValue(mValue)", fieldName)
                    .build();

            MethodSpec postMethod = MethodSpec
                    .methodBuilder("post" + fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(mClazz == null ? liveDataTypeName : mClazz, "mValue")
                    .beginControlFlow("if (this.m$L == null)", fieldName)
                    .addStatement("return")
                    .endControlFlow()
                    .addStatement("this.m$L.postValue(mValue)", fieldName)
                    .build();

            builder.addField(field)
                    .addMethod(getMethod)
                    .addMethod(getValue)
                    .addMethod(setMethod)
                    .addMethod(postMethod);
        }


        TypeSpec typeSpec = builder.build();
        // 指定包名
        return JavaFile.builder("com.zl.weilu.saber.viewmodel", typeSpec).build();
    }

}